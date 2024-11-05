package GS;

import dft.inject.gun.Cannon;
import robocode.*;
import robocode.util.Utils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Random;

public class CR7 extends AdvancedRobot {
    private double absBearing, enemyDistance, enemyVelocity;
    private double lastEnemyVelocity, lastEnemyLatVelocity;
    private double lastVelocityChangeTime;
    private double enemyLatVelocity, enemyAdvVelocity;
    private double moveDirection = 1;
    private double enemyDirection = moveDirection*Math.asin(8D/11D);
    private Point2D.Double myLocation, enemyLocation;
    private Rectangle2D battleField;
    private int previousEnemyDirection = 1;
    private double wallDistance, reverseWallDistance;
    private final double WALL_MARGIN = 36;

    private double finalBulletPower, finalGunTurn;
    private static final int MIDDLE_FACTOR = 16;
    private static final int TOTAL_FACTORS = 33;

    private ArrayList<WaveBullet2> waves = new ArrayList<>();
    //private static double[][][][][] stats = new double[10][3][3][2][31];
    private static double[][][][][][][] statsA = new double[3][5][5][5][10][3][TOTAL_FACTORS];
    private static double[][][][][][][] statsB = new double[3][5][5][5][8][3][TOTAL_FACTORS];

    GunWave root, current;

    private Random random = new Random();
    public static int BINS = 47;
    public static double _surfStats[] = new double[BINS];
    public Point2D.Double _myLocation;     // our bot's location
    public Point2D.Double _enemyLocation;  // enemy bot's location
    public ArrayList _enemyWaves;
    public ArrayList _surfDirections;
    public ArrayList _surfAbsBearings;

    public static double _oppEnergy = 100.0;


    public static Rectangle2D.Double _fieldRect
            = new java.awt.geom.Rectangle2D.Double(18, 18, 764, 564);
    public static double WALL_STICK = 160;

    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setColors(Color.WHITE, new Color(187, 165, 61), new Color(187, 165, 61));
        setScanColor(new Color(187, 165, 61));
        _enemyWaves = new ArrayList();
        _surfDirections = new ArrayList();
        _surfAbsBearings = new ArrayList();
        setTurnRadarRight(Double.POSITIVE_INFINITY);

    }

    public void onScannedRobot(ScannedRobotEvent e) {

        _myLocation = new Point2D.Double(getX(), getY());

        double lateralVelocity = getVelocity()*Math.sin(e.getBearingRadians());
        double absBearing = e.getBearingRadians() + getHeadingRadians();

        _surfDirections.add(0,
                new Integer((lateralVelocity >= 0) ? 1 : -1));
        _surfAbsBearings.add(0, new Double(absBearing + Math.PI));


        double bulletPower = _oppEnergy - e.getEnergy();
        if (bulletPower < 3.01 && bulletPower > 0.09
                && _surfDirections.size() > 2) {
            EnemyWave ew = new EnemyWave();
            ew.fireTime = getTime() - 1;
            ew.bulletVelocity = bulletVelocity(bulletPower);
            ew.distanceTraveled = bulletVelocity(bulletPower);
            ew.direction = ((Integer)_surfDirections.get(2)).intValue();
            ew.directAngle = ((Double)_surfAbsBearings.get(2)).doubleValue();
            ew.fireLocation = (Point2D.Double)_enemyLocation.clone(); // last tick

            _enemyWaves.add(ew);
        }

        _oppEnergy = e.getEnergy();

        _enemyLocation = project(_myLocation, absBearing, e.getDistance());

        updateWaves();
        doSurfing();

        double enemyBearing = e.getBearingRadians();
        double absoluteAngleToEnemy = getHeadingRadians() + enemyBearing;
        double enemyDistance = e.getDistance();
        double enemyX = getX() + enemyDistance * Math.sin(absoluteAngleToEnemy);
        double enemyY = getY() + enemyDistance * Math.cos(absoluteAngleToEnemy);

        for (int i = 0; i < waves.size(); i++) {
            WaveBullet2 currentWave = waves.get(i);
            if (currentWave.checkHit(enemyX, enemyY, getTime())) {
                waves.remove(i);
                i--;
            }
        }
////////////////////////////////////////////////////////////////////////////////////////////////////

        finalBulletPower = 1.9;
        if (e.getDistance() < 240) finalBulletPower = 3.0;
        finalBulletPower = Math.min(finalBulletPower, e.getEnergy()/4);
        finalBulletPower = Math.min(finalBulletPower, getEnergy()/2);

        double bulletSpeed = Rules.getBulletSpeed(finalBulletPower);
        lastEnemyLatVelocity = enemyLatVelocity;
        lastEnemyVelocity = enemyVelocity;
        enemyVelocity = e.getVelocity();

        absBearing = e.getBearingRadians() + getHeadingRadians();
        myLocation = new Point2D.Double(getX(), getY());
        enemyDistance = e.getDistance();
        enemyLocation = project(myLocation, absBearing, enemyDistance);
        battleField = new Rectangle2D.Double(18, 18, getBattleFieldWidth() - WALL_MARGIN, getBattleFieldHeight() - WALL_MARGIN);

        // Determine the enemy's lateral velocity and enemy direction (escape envelope)
        enemyLatVelocity = enemyVelocity*Math.sin(e.getHeadingRadians() - absBearing);
        if (enemyLatVelocity != 0)
            moveDirection = (enemyLatVelocity > 0 ? 1 : -1);
        enemyDirection = moveDirection * Math.asin(8/bulletSpeed);

        // Returns the enemy's lateral distance to wall as a value between 0 and 1
        wallDistance = 1.1;
        while (wallDistance >= 0.1 && !battleField.contains(
                project(myLocation,
                        absBearing + (wallDistance -= 0.1) * enemyDirection,
                        enemyDistance)));


        reverseWallDistance = 1.1;
        while (reverseWallDistance >= 0.1 && !battleField.contains(
                project(myLocation,
                        absBearing - (reverseWallDistance-=0.1)*enemyDirection,
                        enemyDistance)));

        // Timer, or velocity change
        double moveTime = bulletSpeed*lastVelocityChangeTime++/enemyDistance;

        int bestIndex = MIDDLE_FACTOR;
        if (e.getEnergy() > 0 && getEnergy() > 0) {

            // Segments
            int distanceIndex = (int) enemyDistance / 240;
            int fastDistanceIndex = (int) enemyDistance / 360;

            int wallIndex = (int) (wallDistance * 3);
            int fastWallIndex = (int) (wallDistance * 1.5);

            int reverseWallIndex = (int) (reverseWallDistance * 2);
            int fastReverseWallIndex = (int) (reverseWallDistance * 1.25);

            int velIndex = (int) Math.abs(enemyLatVelocity / 2);
            int fastVelIndex = (int) Math.abs(enemyLatVelocity / 2.67);

            int timerIndex = moveTime < .4 ? 1 : moveTime < .8 ? 2 : moveTime < 1.2 ? 3 : 4;
            int fastTimerIndex = moveTime < .6 ? 1 : 2;

            int accelIndex = (int) Math.round(Math.abs(enemyVelocity) - Math.abs(lastEnemyVelocity));

            if (accelIndex != 0){
                accelIndex = accelIndex > 0 ? 2 : 1;
            }

            if (accelIndex > 0) {
                lastVelocityChangeTime = 0;
                timerIndex = fastTimerIndex = 0;
                //accelIndex = (int)Math.round(Math.abs(enemyVelocity) - Math.abs(lastEnemyVelocity));
                //velIndex = (int)Math.abs(enemyVelocity/2.67);
                //fastVelIndex = (int)Math.abs(enemyVelocity/4.01);
            }

            GunWave g = new GunWave();

            g.bulletOrigin = myLocation;
            g.targetOrigin = g.currentTarget = project(enemyLocation, e.getHeadingRadians(), -enemyVelocity);
            g.bulletAngle = absoluteBearing(g.bulletOrigin, g.targetOrigin);
            g.bulletVelocity = bulletSpeed;
            g.fireTime = g.lastTime = getTime() - 1;
            g.escapeEnvelope = enemyDirection;

            g.aSeg = statsA[accelIndex][velIndex][timerIndex][wallIndex][distanceIndex][reverseWallIndex];
            g.bSeg = statsB[accelIndex][fastVelIndex][fastTimerIndex][fastWallIndex][fastDistanceIndex][fastReverseWallIndex];

            if (getGunHeat() == 0) g.real = true;

            // This adds the wave appropriately to the list
            if (root == null)
                root = current = g;
            else
                current = (current.next = g);

            while (root != null && root.update(getTime(), enemyLocation)) {
                root = root.next;
            }
            if (root != null) {
                GunWave waveIterator = root.next;
                while (waveIterator != null) {
                    waveIterator.update(getTime(), enemyLocation);
                    waveIterator = waveIterator.next;
                }
            }

            // Search for the best index
            double bestA = smoothed(bestIndex, g.aSeg);
            double bestB = smoothed(bestIndex, g.bSeg);
            for (int i = MIDDLE_FACTOR*2-1; i >= 1; i--) {
                double aCur = smoothed(i, g.aSeg);
                double bCur = smoothed(i, g.bSeg);
                if (aCur + bCur > bestA + bestB) {
                    //if (battleField.contains(Utils.project(myLocation,robocode.util.Utils.normalRelativeAngle(absBearing+enemyDirection*(i/(double)MIDDLE_FACTOR-1)),enemyDistance)));
                    bestIndex = i;
                    bestA = aCur;
                    bestB = bCur;
                }
                //if (g.aSeg[i]+g.bSeg[i]> g.aSeg[bestIndex]+g.bSeg[bestIndex])
                //	bestIndex = i;
            }
            if (getGunHeat() < getGunCoolingRate()*3)
                finalGunTurn = Utils.normalRelativeAngle(absBearing-getGunHeadingRadians()+enemyDirection*(bestIndex/(double)MIDDLE_FACTOR-1));
            else
                finalGunTurn = Utils.normalRelativeAngle(absBearing-getGunHeadingRadians());


            setTurnGunRightRadians(finalGunTurn);
            if (getBulletPower() > 0){
                setFire(getBulletPower());
            }

            setTurnRadarRightRadians(Math.tan(e.getBearingRadians()+getHeadingRadians()-getRadarHeadingRadians())*1.94);


        }


//        firePower = Math.min(3, getEnergy());
//        bulletSpeed = 20 - 3 * firePower;
//
//        int enemyDirection = 1;
//
//        if (e.getVelocity() != 0) {
//            if (Math.sin(e.getHeadingRadians() - absoluteAngleToEnemy) * e.getVelocity() < 0) {
//                enemyDirection = -1;
//            }
//        }
//
//        if (enemyDirection != previousEnemyDirection) {
//            previousEnemyDirection = enemyDirection;
//        }
//
//        int distanceIndex = Math.min(9, (int)(enemyDistance / 50));
//        int acceleration = (int)Math.round(Math.abs(lastEnemyVelocity) - Math.abs(lastEnemyVelocity = e.getVelocity()));
//        if (acceleration != 0)
//            acceleration = (acceleration < 0) ? 1 : 2;
//
//        double enemyLateralVelocity = Math.sin(e.getHeadingRadians() - absoluteAngleToEnemy) * lastEnemyVelocity;
//        int lateralVelocityIndex = (int)Math.abs(enemyLateralVelocity / 3);
//        int enemyDirectionChange = (enemyDirection == 1) ? 1 : 0;
//
//        double[] currentStats = stats[distanceIndex][lateralVelocityIndex][acceleration][enemyDirectionChange];
//
//        WaveBullet2 newWave = new WaveBullet2(
//                getX(), getY(), absoluteAngleToEnemy, firePower, enemyDirection, getTime(), currentStats);
//        waves.add(newWave);
//
//        int bestIndex = findBestIndex(currentStats);
//
//        double guessFactor = (double) (bestIndex - (currentStats.length - 1) / 2) / ((currentStats.length - 1) / 2);
//        System.out.println("guessFactor utilizado: " + guessFactor);
//        double angleOffset = enemyDirection * guessFactor * newWave.getMaximumEscapeAngle();
//        double gunAdjust = Utils.normalRelativeAngle(absoluteAngleToEnemy - getGunHeadingRadians() + angleOffset);
//        setTurnGunRightRadians(gunAdjust);
//
//        if (getGunHeat() == 0 && Math.abs(gunAdjust) < Math.atan2(9, enemyDistance)) {
//            setFire(firePower);
//        }
    }

    public void onHitByBullet(HitByBulletEvent e) {
        if (!_enemyWaves.isEmpty()) {
            Point2D.Double hitBulletLocation = new Point2D.Double(
                    e.getBullet().getX(), e.getBullet().getY());
            EnemyWave hitWave = null;

            for (int x = 0; x < _enemyWaves.size(); x++) {
                EnemyWave ew = (EnemyWave)_enemyWaves.get(x);

                if (Math.abs(ew.distanceTraveled -
                        _myLocation.distance(ew.fireLocation)) < 50
                        && Math.abs(bulletVelocity(e.getBullet().getPower())
                        - ew.bulletVelocity) < 0.001) {
                    hitWave = ew;
                    break;
                }
            }

            if (hitWave != null) {
                logHit(hitWave, hitBulletLocation);

                _enemyWaves.remove(_enemyWaves.lastIndexOf(hitWave));
            }
        }
    }

    public void onHitWall(HitWallEvent e) {
        moveDirection *= -1;
        setBack(100);
    }

    public void onWin(WinEvent e) {
        System.out.println("EU SOU O MILIOR");
        turnGunRight(Double.POSITIVE_INFINITY);
    }

    public void updateWaves() {
        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);

            ew.distanceTraveled = (getTime() - ew.fireTime) * ew.bulletVelocity;
            if (ew.distanceTraveled >
                    _myLocation.distance(ew.fireLocation) + 50) {
                _enemyWaves.remove(x);
                x--;
            }
        }
    }

    public EnemyWave getClosestSurfableWave() {
        double closestDistance = Double.POSITIVE_INFINITY;
        EnemyWave surfWave = null;

        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
            double distance = _myLocation.distance(ew.fireLocation)
                    - ew.distanceTraveled;

            if (distance > ew.bulletVelocity && distance < closestDistance) {
                surfWave = ew;
                closestDistance = distance;
            }
        }

        return surfWave;
    }

    public static int getFactorIndex(EnemyWave ew, Point2D.Double targetLocation) {
        double offsetAngle = (absoluteBearing(ew.fireLocation, targetLocation)
                - ew.directAngle);
        double factor = Utils.normalRelativeAngle(offsetAngle)
                / maxEscapeAngle(ew.bulletVelocity) * ew.direction;

        return (int)limit(0,
                (factor * ((BINS - 1) / 2)) + ((BINS - 1) / 2),
                BINS - 1);
    }

    public void logHit(EnemyWave ew, Point2D.Double targetLocation) {
        int index = getFactorIndex(ew, targetLocation);

        for (int x = 0; x < BINS; x++) {
            _surfStats[x] += 1.0 / (Math.pow(index - x, 2) + 1);
        }
    }

    public Point2D.Double predictPosition(EnemyWave surfWave, int direction) {
        Point2D.Double predictedPosition = (Point2D.Double)_myLocation.clone();
        double predictedVelocity = getVelocity();
        double predictedHeading = getHeadingRadians();
        double maxTurning, moveAngle, moveDir;

        int counter = 0;
        boolean intercepted = false;

        do {
            moveAngle =
                    wallSmoothing(predictedPosition, absoluteBearing(surfWave.fireLocation,
                            predictedPosition) + (direction * (Math.PI/2)), direction)
                            - predictedHeading;
            moveDir = 1;

            if(Math.cos(moveAngle) < 0) {
                moveAngle += Math.PI;
                moveDir = -1;
            }

            moveAngle = Utils.normalRelativeAngle(moveAngle);

            maxTurning = Math.PI/720d*(40d - 3d*Math.abs(predictedVelocity));
            predictedHeading = Utils.normalRelativeAngle(predictedHeading
                    + limit(-maxTurning, moveAngle, maxTurning));

            predictedVelocity +=
                    (predictedVelocity * moveDir < 0 ? 2*moveDir : moveDir);
            predictedVelocity = limit(-8, predictedVelocity, 8);


            predictedPosition = project(predictedPosition, predictedHeading,
                    predictedVelocity);

            counter++;

            if (predictedPosition.distance(surfWave.fireLocation) <
                    surfWave.distanceTraveled + (counter * surfWave.bulletVelocity)
                            + surfWave.bulletVelocity) {
                intercepted = true;
            }
        } while(!intercepted && counter < 500);

        return predictedPosition;
    }

    public double checkDanger(EnemyWave surfWave, int direction) {
        int index = getFactorIndex(surfWave,
                predictPosition(surfWave, direction));

        return _surfStats[index];
    }

    public void doSurfing() {
        EnemyWave surfWave = getClosestSurfableWave();

        if (surfWave == null) { return; }

        double dangerLeft = checkDanger(surfWave, -1);
        double dangerRight = checkDanger(surfWave, 1);

        double goAngle = absoluteBearing(surfWave.fireLocation, _myLocation);
        if (dangerLeft < dangerRight) {
            goAngle = wallSmoothing(_myLocation, goAngle - (Math.PI/2), -1);
        } else {
            goAngle = wallSmoothing(_myLocation, goAngle + (Math.PI/2), 1);
        }

        setBackAsFront(this, goAngle);
    }

    class EnemyWave {
        Point2D.Double fireLocation;
        long fireTime;
        double bulletVelocity, directAngle, distanceTraveled;
        int direction;

        public EnemyWave() { }
    }

    class GunWave {
        GunWave next;
        Point2D.Double bulletOrigin, targetOrigin;
        Point2D.Double currentTarget;
        double bulletAngle, bulletVelocity, escapeEnvelope;
        long fireTime, lastTime;

        double[] aSeg, bSeg;

        boolean real = false;

        boolean update(long time, Point2D enemy) {
            long dTime;
            double dX = (enemy.getX() - currentTarget.getX()) / (dTime = time - lastTime);
            double dY = (enemy.getY() - currentTarget.getY()) / dTime;
            do {
                if (bulletOrigin.distance(currentTarget) <= bulletVelocity * (lastTime - fireTime)) {

                    int index = (int) findGF(bulletAngle, absoluteBearing(bulletOrigin, currentTarget), escapeEnvelope, MIDDLE_FACTOR);
                    index = (int) minMax(1, MIDDLE_FACTOR * 2 - 1, index);

                    double weightReal = 1;
                    if (real) weightReal = 5;

                    // This rolls the previously saved data (necessary to keep it as a percentage)
                    for (int i = 1; i < MIDDLE_FACTOR * 2; i++) {
                        aSeg[i] *= aSeg[0] / (aSeg[0] + weightReal);
                        bSeg[i] *= bSeg[0] / (bSeg[0] + weightReal);
                    }
                    // Update the appropriate buckets, [0] is the total number of hits in that aSeg
                    aSeg[0] += weightReal;
                    aSeg[index] += (weightReal / aSeg[0]);

                    bSeg[0] += weightReal;
                    bSeg[index] += (weightReal / bSeg[0]);

                    return true;
                }
                lastTime++;
                currentTarget.setLocation(currentTarget.getX() + dX, currentTarget.getY() + dY);
            }
            while (lastTime < time);
            return false;
        }
    }

    public double wallSmoothing(Point2D.Double botLocation, double angle, int orientation) {
        while (!_fieldRect.contains(project(botLocation, angle, WALL_STICK))) {
            angle += orientation*0.05;
        }
        return angle;
    }

    public static Point2D.Double project(Point2D.Double sourceLocation,
                                         double angle, double length) {
        return new Point2D.Double(sourceLocation.x + Math.sin(angle) * length,
                sourceLocation.y + Math.cos(angle) * length);
    }

    public static double absoluteBearing(Point2D.Double source, Point2D.Double target) {
        return Math.atan2(target.x - source.x, target.y - source.y);
    }

    public static double limit(double min, double value, double max) {
        return Math.max(min, Math.min(value, max));
    }

    public static double bulletVelocity(double power) {
        return (20.0 - (3.0*power));
    }

    public static double maxEscapeAngle(double velocity) {
        return Math.asin(8.0/velocity);
    }

    public static void setBackAsFront(AdvancedRobot robot, double goAngle) {
        double angle =
                Utils.normalRelativeAngle(goAngle - robot.getHeadingRadians());
        if (Math.abs(angle) > (Math.PI/2)) {
            if (angle < 0) {
                robot.setTurnRightRadians(Math.PI + angle);
            } else {
                robot.setTurnLeftRadians(Math.PI - angle);
            }
            robot.setBack(100);
        } else {
            if (angle < 0) {
                robot.setTurnLeftRadians(-1*angle);
            } else {
                robot.setTurnRightRadians(angle);
            }
            robot.setAhead(100);
        }
    }

    private int findBestIndex(double[] statsSegment) {
        int bestIndex = statsSegment.length / 2;
        double maxWeightedHits = 0;

        for (int i = 0; i < statsSegment.length; i++) {
            double weightedHits = statsSegment[i];

            // Apply dynamic smoothing: closer bins get higher weights
            if (i > 1) weightedHits += statsSegment[i - 2] * 0.2;
            if (i > 0) weightedHits += statsSegment[i - 1] * 0.5;
            if (i < statsSegment.length - 1) weightedHits += statsSegment[i + 1] * 0.5;
            if (i < statsSegment.length - 2) weightedHits += statsSegment[i + 2] * 0.2;

            if (weightedHits > maxWeightedHits) {
                maxWeightedHits = weightedHits;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

//    private int findBestIndex(double[] statsSegment){
//        int mostVisited = 15;
//        for (int i = 0; i < BINS; i++) {
//            if (statsSegment[i] > statsSegment[mostVisited]) {
//                mostVisited = i;
//            }
//        }
//        return mostVisited;
//    }

    public double getGunTurn() {
        return finalGunTurn;
    }

    public double getBulletPower() {
        return finalBulletPower;
    }

    public  double findGF(double startAngle, double newAngle, double escapeEnvelope, double middleFactor) {
        return (int)minMax(1,middleFactor*2-1,Math.round((1+robocode.util.Utils.normalRelativeAngle(newAngle-startAngle)/escapeEnvelope)*middleFactor));
    }

    public double minMax(double min, double max, double value) {
        if (value > max)
            return max;
        else if (value < min)
            return min;
        else
            return value;
    }

    public static double smoothed(int gf, double[] curr) {
        double x = 0;
        for (int y = 1; y < TOTAL_FACTORS-1; y++) {
            x += curr[y]/ Math.sqrt((Math.abs(gf - y) + 1.0));
        }
        return x;
    }

}

