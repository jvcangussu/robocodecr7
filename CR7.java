package cr7;

import robocode.*;
import robocode.util.Utils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

public class CR7 extends AdvancedRobot {
    private double enemyVelocity;
    private double lastEnemyVelocity;
    private double enemyLateralVelocity;
    private double lastVelocityChangeTime;
    private double moveDirection = 1;
    private double escapeEnvelope = moveDirection * maxEscapeAngle(Rules.getBulletSpeed(3));
    private Point2D.Double myLocation;
    private Point2D.Double enemyLocation;
    public static double enemyEnergy = 100.0;
    private double wallDistance;
    private double reverseWallDistance;
    private final double WALL_MARGIN = 36;
    public static double WALL_STICK = 160;
    private Rectangle2D battleField;

    //Variaveis para controle de tiro
    private double finalBulletPower;
    private double finalGunTurn;
    private static final int MIDDLE_GUN_FACTOR = 16;
    private static final int TOTAL_GUN_FACTORS = 33;

    private static double[][][][][][][] normalGunSegmentation = new double[3][5][5][5][10][3][TOTAL_GUN_FACTORS];
    private static double[][][][][][][] fastGunSegmentation = new double[3][5][5][5][8][3][TOTAL_GUN_FACTORS];

    GunWave head;
    GunWave current;

    //Variaveis para controle de movimentacao
    public static final int MIDDLE_MOVE_FACTOR = 23;
    public static final int TOTAL_MOVE_FACTORS = 47;
    public static double moveSegmentation[] = new double[TOTAL_MOVE_FACTORS];
    public ArrayList<EnemyWave> enemyWaves;
    public ArrayList surfDirections;
    public ArrayList surfAbsoluteBearings;

    //Metodos da anatomia do robo
    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setColors(Color.WHITE, new Color(187, 165, 61), new Color(187, 165, 61));
        setScanColor(new Color(187, 165, 61));
        battleField = new Rectangle2D.Double(18, 18, getBattleFieldWidth() - WALL_MARGIN, getBattleFieldHeight() - WALL_MARGIN);
        enemyWaves = new ArrayList();
        surfDirections = new ArrayList();
        surfAbsoluteBearings = new ArrayList();
        setTurnRadarRight(Double.POSITIVE_INFINITY);
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        //Movimentacao por WaveSurfing

        myLocation = new Point2D.Double(getX(), getY());
        double lateralVelocity = getVelocity() * Math.sin(e.getBearingRadians());
        double absoluteBearing = e.getBearingRadians() + getHeadingRadians();

        surfDirections.add(0, (lateralVelocity >= 0) ? 1 : -1);
        surfAbsoluteBearings.add(0, new Double(absoluteBearing + Math.PI));

        double enemyBulletPower = enemyEnergy - e.getEnergy();
        //Se a diferenca de energia do inimigo estiver no intervalo possivel de poder de fogo significa que ele atirou
        if (enemyBulletPower < 3.01 && enemyBulletPower > 0.09 && surfDirections.size() > 2) {
            //Adicionar uma nova enemy wave para tal evento
            EnemyWave ew = new EnemyWave();
            ew.fireTime = getTime() - 1;
            ew.bulletVelocity = Rules.getBulletSpeed(enemyBulletPower);
            ew.distanceTraveled = Rules.getBulletSpeed(enemyBulletPower);
            ew.direction = (Integer) surfDirections.get(2);
            ew.directAngle = (Double) surfAbsoluteBearings.get(2);
            ew.fireLocation = (Point2D.Double)enemyLocation.clone(); // last tick
            enemyWaves.add(ew);
        }

        enemyEnergy = e.getEnergy();

        enemyLocation = projectCoordinates(myLocation, absoluteBearing, e.getDistance());

        updateWaves();
        doSurfing();

        //Arma do robo utilizando Guess Factor Gun

        finalBulletPower = 1.9;
        //Estrategia de aumentar o poder de fogo caso o inimigo esteja perto
        if (e.getDistance() < 240) finalBulletPower = 3.0;
        //Estrategia de calcular o poder de fogo pela energia do inimigo e propria energia
        finalBulletPower = Math.min(finalBulletPower, e.getEnergy()/4);
        finalBulletPower = Math.min(finalBulletPower, getEnergy()/2);
        double bulletSpeed = Rules.getBulletSpeed(finalBulletPower);

        lastEnemyVelocity = enemyVelocity;
        enemyVelocity = e.getVelocity();
        absoluteBearing = e.getBearingRadians() + getHeadingRadians();
        myLocation = new Point2D.Double(getX(), getY());
        double enemyDistance = e.getDistance();
        enemyLocation = projectCoordinates(myLocation, absoluteBearing, enemyDistance);

        //Encontra a velocidade lateral e direcao do inimigo para determinar o escape envelope
        enemyLateralVelocity = enemyVelocity * Math.sin(e.getHeadingRadians() - absoluteBearing);
        if (enemyLateralVelocity != 0)
            moveDirection = (enemyLateralVelocity > 0 ? 1 : -1);
        escapeEnvelope = moveDirection * maxEscapeAngle(bulletSpeed);

        //Retorna a distancia lateral do inimigo ate a parede como um valor entre 0 e 1
        wallDistance = 1.1;

        while(wallDistance >= 0.1){
            wallDistance -= 0.1;
            Point2D.Double predictedBulletPosition = projectCoordinates(myLocation,absoluteBearing + wallDistance * escapeEnvelope, enemyDistance);
            if(battleField.contains(predictedBulletPosition))
                break;
        }

        //Faz o mesmo calculo para o outro lado
        reverseWallDistance = 1.1;
        while(reverseWallDistance >= 0.1){
            reverseWallDistance -= 0.1;
            Point2D.Double predictedBulletPosition = projectCoordinates(myLocation,absoluteBearing - reverseWallDistance * escapeEnvelope, enemyDistance);
            if(battleField.contains(predictedBulletPosition))
                break;
        }

        //Fator de alteracao de velocidade de movimento do inimigo para detectar robos imprevisiveis
        double moveTime = bulletSpeed * lastVelocityChangeTime++ / enemyDistance;

        //Segmentacao
        if (e.getEnergy() > 0 && getEnergy() > 0) {
            //Segmentacao pela distancia ate o inimigo
            int distanceIndex = (int) enemyDistance / 240;
            int fastDistanceIndex = (int) enemyDistance / 360;

            //Segmentacao pela distancia do inimigo ate a parede
            int nearWallIndex = (int) (wallDistance * 3);
            int fastNearWallIndex = (int) (wallDistance * 1.5);

            //Segmentacao pela distancia do inimigo ate a parede inversa
            int reverseNearWallIndex = (int) (reverseWallDistance * 2);
            int fastReverseNearWallIndex = (int) (reverseWallDistance * 1.25);

            //Segmentacao pela velocidade lateral do inimigo
            int lateralVelocityIndex = (int) Math.abs(enemyLateralVelocity / 2);
            int fastLateralVelocityIndex = (int) Math.abs(enemyLateralVelocity / 2.67);

            //Segmentacao pelo tempo desde a ultima mudanca de velocidade do inimigo
            int moveTimeIndex = moveTime < .4 ? 1 : moveTime < .8 ? 2 : moveTime < 1.2 ? 3 : 4;
            int fastMoveTimeIndex = moveTime < .6 ? 1 : 2;

            //Segmentacao pela aceleracao do inimigo
            int accelerationIndex = (int) Math.round(Math.abs(enemyVelocity) - Math.abs(lastEnemyVelocity));
            if (accelerationIndex != 0){
                accelerationIndex = accelerationIndex > 0 ? 2 : 1;
            }
            if (accelerationIndex > 0) {
                lastVelocityChangeTime = 0;
                moveTimeIndex = fastMoveTimeIndex = 0;
            }

            //Determina uma nova onda de tiro
            GunWave g = new GunWave();
            g.bulletOrigin = myLocation;
            g.enemyOrigin = g.lastEnemyPosition = projectCoordinates(enemyLocation, e.getHeadingRadians(), -enemyVelocity);
            g.bulletAngle = absoluteBearing(g.bulletOrigin, g.enemyOrigin);
            g.bulletVelocity = bulletSpeed;
            g.fireTime = g.lastTime = getTime() - 1;
            g.escapeEnvelope = escapeEnvelope;
            g.normalSegment = normalGunSegmentation[accelerationIndex][lateralVelocityIndex][moveTimeIndex][nearWallIndex][distanceIndex][reverseNearWallIndex];
            g.fastSegment = fastGunSegmentation[accelerationIndex][fastLateralVelocityIndex][fastMoveTimeIndex][fastNearWallIndex][fastDistanceIndex][fastReverseNearWallIndex];

            //Se o cooldown da arma for 0 significa que esta apta a dar um tiro real que sera utilizado para ponderar os dados
            if (getGunHeat() == 0){
                g.real = true;
            }

            //Utiliza linked list para salvar as ondas de tiro
            if (head == null)
                head = current = g;
            else
                current = (current.next = g);

            //Percorre a lista ligada iterando sobre as waves ate chegar em uma que ainda nao atingiu o oponente
            //Ao fazer isso, remove as que ja nao sao mais uteis
            while (head != null){
                if(!head.update(getTime(), enemyLocation)){
                    break;
                }
                head = head.next;
            }

            //Percorre as ondas restantes atualizando a posicao dessas no tempo atual
            if (head != null) {
                GunWave waveIterator = head.next;
                while (waveIterator != null) {
                    waveIterator.update(getTime(), enemyLocation);
                    waveIterator = waveIterator.next;
                }
            }

            int bestIndex = MIDDLE_GUN_FACTOR;
            //Procura pelos melhores indexes dada as segmentacoes
            double bestWeightedValueFromNormalSegment = weightedHitsSmoothing(bestIndex, g.normalSegment);
            double bestWeightedValueFromFastSegment = weightedHitsSmoothing(bestIndex, g.fastSegment);
            for (int i = MIDDLE_GUN_FACTOR * 2 - 1; i >= 1; i--) {
                double currentNormalWeightedValue = weightedHitsSmoothing(i, g.normalSegment);
                double currentFastWeightedValue = weightedHitsSmoothing(i, g.fastSegment);
                if (currentNormalWeightedValue + currentFastWeightedValue > bestWeightedValueFromNormalSegment + bestWeightedValueFromFastSegment) {
                    bestIndex = i;
                    bestWeightedValueFromNormalSegment = currentNormalWeightedValue;
                    bestWeightedValueFromFastSegment = currentFastWeightedValue;
                }
            }

            //Ajusta o angulo final de mira da arma de acordo com a possibilidade de atirar realmente ou nao
            if (getGunHeat() < getGunCoolingRate() * 3)
                finalGunTurn = Utils.normalRelativeAngle(absoluteBearing - getGunHeadingRadians() + escapeEnvelope * (bestIndex/(double) MIDDLE_GUN_FACTOR - 1));
            else
                finalGunTurn = Utils.normalRelativeAngle(absoluteBearing-getGunHeadingRadians());

            //Gira a arma e atira
            setTurnGunRightRadians(finalGunTurn);
            if (finalBulletPower > 0){
                setFire(finalBulletPower);
            }

            setTurnRadarRightRadians(Math.tan(e.getBearingRadians() + getHeadingRadians() - getRadarHeadingRadians()) * 1.95);
        }
    }

    public void onHitByBullet(HitByBulletEvent e) {
        if (!enemyWaves.isEmpty()) {
            Point2D.Double hitBulletLocation = new Point2D.Double(
                    e.getBullet().getX(), e.getBullet().getY());
            EnemyWave hitWave = null;

            for (int x = 0; x < enemyWaves.size(); x++) {
                EnemyWave ew = (EnemyWave) enemyWaves.get(x);

                if (Math.abs(ew.distanceTraveled -
                        myLocation.distance(ew.fireLocation)) < 50
                        && Math.abs(Rules.getBulletSpeed(e.getBullet().getPower())
                        - ew.bulletVelocity) < 0.001) {
                    hitWave = ew;
                    break;
                }
            }

            if (hitWave != null) {
                logHit(hitWave, hitBulletLocation);

                enemyWaves.remove(enemyWaves.lastIndexOf(hitWave));
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

    //Metodos para controle de tiro

    //Metodos para controle de movimentacao

    //Metodo updateWaves
    //Atualiza a lista de waves do inimigo removendo aquelas que ja passaram pelo robo
    public void updateWaves() {
        for (int i = 0; i < enemyWaves.size(); i++) {
            EnemyWave ew = enemyWaves.get(i);
            double deltaTime = getTime() - ew.fireTime;
            ew.distanceTraveled = deltaTime * ew.bulletVelocity;
            if (ew.distanceTraveled > myLocation.distance(ew.fireLocation) + 50) {
                enemyWaves.remove(i);
                i--;
            }
        }
    }

    public void doSurfing() {
        EnemyWave surfWave = getClosestSurfableWave();
        if (surfWave == null) {
            return;
        }

        double dangerLeft = checkDanger(surfWave, -1);
        double dangerRight = checkDanger(surfWave, 1);

        double goAngle = absoluteBearing(surfWave.fireLocation, myLocation);
        if (dangerLeft < dangerRight) {
            goAngle = wallSmoothing(myLocation, goAngle - (Math.PI/2), -1);
        } else {
            goAngle = wallSmoothing(myLocation, goAngle + (Math.PI/2), 1);
        }

        setBackAsFront(this, goAngle);
    }

    //Metodo getClosestSurfableWave
    //Retorna a wave mais proxima do robo ou seja a que ele ira surfar
    public EnemyWave getClosestSurfableWave() {
        double closestDistance = Double.POSITIVE_INFINITY;
        EnemyWave surfWave = null;
        for (int i = 0; i < enemyWaves.size(); i++) {
            EnemyWave ew = enemyWaves.get(i);
            double distance = myLocation.distance(ew.fireLocation) - ew.distanceTraveled;
            if (distance > ew.bulletVelocity && distance < closestDistance) {
                surfWave = ew;
                closestDistance = distance;
            }
        }
        return surfWave;
    }

    //Metodo checkDanger
    //Checa o perigo que uma enemy wave representa caso o robo se mova para uma determinada direcao
    public double checkDanger(EnemyWave surfWave, int direction) {
        int index = getMoveFactorIndex(surfWave, predictPosition(surfWave, direction));
        return moveSegmentation[index];
    }

    //Metodo getMoveFactorIndex
    //Retorna o index do guess factor em que uma wave interceptaria o robo na posicao prevista
    public int getMoveFactorIndex(EnemyWave ew, Point2D.Double predictedLocation) {
        double offsetAngle = (absoluteBearing(ew.fireLocation, predictedLocation) - ew.directAngle);
        double factor = Utils.normalRelativeAngle(offsetAngle) / maxEscapeAngle(ew.bulletVelocity) * ew.direction;
        return (int)minMax(0,TOTAL_MOVE_FACTORS - 1, (factor * (MIDDLE_MOVE_FACTOR) + (MIDDLE_MOVE_FACTOR)));
    }

    //Metodo predictPosition
    public Point2D.Double predictPosition(EnemyWave surfWave, int direction) {
        Point2D.Double predictedPosition = (Point2D.Double)myLocation.clone();
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
                    + minMax(-maxTurning, maxTurning, moveAngle));

            predictedVelocity +=
                    (predictedVelocity * moveDir < 0 ? 2*moveDir : moveDir);
            predictedVelocity = minMax(-8, 8, predictedVelocity);


            predictedPosition = projectCoordinates(predictedPosition, predictedHeading,
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

    public void logHit(EnemyWave ew, Point2D.Double targetLocation) {
        int index = getMoveFactorIndex(ew, targetLocation);

        for (int x = 0; x < TOTAL_MOVE_FACTORS; x++) {
            moveSegmentation[x] += 1.0 / (Math.pow(index - x, 2) + 1);
        }
    }

    public double wallSmoothing(Point2D.Double botLocation, double angle, int orientation) {
        while (!battleField.contains(projectCoordinates(botLocation, angle, WALL_STICK))) {
            angle += orientation*0.05;
        }
        return angle;
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

    //Metodos utilitarios

    //Metodo projectCoordinates
    //Recebe um ponto de origem e coordenadas polares e retorna as coordenadas cartesianas da projecao
    public static Point2D.Double projectCoordinates(Point2D.Double originPoint, double angle, double distance) {
        Point2D.Double targetCoordinates = new Point2D.Double();
        targetCoordinates.x = originPoint.x + Math.sin(angle) * distance;
        targetCoordinates.y = originPoint.y + Math.cos(angle) * distance;
        return targetCoordinates;
    }

    //Metodo absoluteBearing
    //Recebe dois pontos e retorna o angulo absoluto (valor entre 0 e 360) entre a origem e o alvo
    public static double absoluteBearing(Point2D.Double originPoint, Point2D.Double targetPoint) {
        return Math.atan2(targetPoint.x - originPoint.x, targetPoint.y - originPoint.y);
    }

    //Metodo maxEscapeAngle
    //Recebe a velocidade da bala e retorna o maior angulo no qual o inimigo poderia ser atingido
    public static double maxEscapeAngle(double bulletVelocity) {
        return Math.asin(8.0/bulletVelocity);
    }

    //Metodo findGuessFactorIndex
    //Encontra o index referente ao guess factor que atingiu o oponente
    public int findGuessFactorIndex(double startAngle, double newAngle, double escapeEnvelope) {
        double guessFactor = Utils.normalRelativeAngle(newAngle - startAngle) / escapeEnvelope;
        int guessFactorIndex = (int)Math.round((1 + guessFactor) * MIDDLE_GUN_FACTOR);
        return (int)minMax(1, TOTAL_GUN_FACTORS - 1, guessFactorIndex);
    }

    //Metodo minMax
    //Recebe um limite inferior e superior e um valor a ser testado
    //caso o valor ultrapasse um dos limites, retorna o limite, caso contrario retorna o proprio valor
    public double minMax(double min, double max, double value) {
        if (value > max)
            return max;
        else if (value < min)
            return min;
        else
            return value;
    }

    //Metodo weightedHitsSmoothing
    //Determina a densidade de acertos de um index de acordo com os outros indexes ponderando pela distancia ate eles
    public static double weightedHitsSmoothing(int guessFactorIndex, double[] currentSegment) {
        double weightedSum = 0;
        for (int i = 1; i < TOTAL_GUN_FACTORS - 1; i++) {
            int distanceToGFIndex = Math.abs(guessFactorIndex - i);
            weightedSum += currentSegment[i] / Math.sqrt(distanceToGFIndex + 1.0);
        }
        return weightedSum;
    }

    //Classes utilitarias
    class EnemyWave {
        Point2D.Double fireLocation;
        long fireTime;
        double bulletVelocity;
        double directAngle;
        double distanceTraveled;
        int direction;
    }

    //Classe GunWave
    //Representa uma onda de tiro que pode ser virtual ou real do robo
    class GunWave {
        GunWave next;
        Point2D.Double bulletOrigin;
        Point2D.Double enemyOrigin;
        Point2D.Double lastEnemyPosition;
        double bulletAngle;
        double bulletVelocity;
        double escapeEnvelope;
        long fireTime;
        long lastTime;
        double[] normalSegment;
        double[] fastSegment;
        boolean real = false;

        boolean update(long time, Point2D currentEnemyPosition) {
            long deltaTime = time - lastTime;
            double deltaX = (currentEnemyPosition.getX() - lastEnemyPosition.getX()) / deltaTime;
            double deltaY = (currentEnemyPosition.getY() - lastEnemyPosition.getY()) / deltaTime;
            do {
                //Se a distancia percorrida pela bala for maior que a distancia da origem da bala ate a posicao do inimigo
                if (bulletOrigin.distance(lastEnemyPosition) <= bulletVelocity * (lastTime - fireTime)) {
                    //Encontra o guess factor da wave e o index referente a esse guess factor para preencher os dados
                    int index = findGuessFactorIndex(bulletAngle, absoluteBearing(bulletOrigin, lastEnemyPosition), escapeEnvelope);
                    index = (int) minMax(1, MIDDLE_GUN_FACTOR * 2 - 1, index);

                    //Atribui um peso maior caso a wave represente um tiro real
                    double weightReal = real ? 5 : 1;

                    //Ajusta os dados ja salvos anteriormente mantendo todos como uma porcentagem
                    for (int i = 1; i < MIDDLE_GUN_FACTOR * 2; i++) {
                        //o index 0 de cada segmentacao representa o numero total de acertos registrados sendo utilizado para ponderar os valores
                        normalSegment[i] *= normalSegment[0] / (normalSegment[0] + weightReal);
                        fastSegment[i] *= fastSegment[0] / (fastSegment[0] + weightReal);
                    }

                    //Atualiza o numero total de acertos adicionando o peso da onda atual
                    normalSegment[0] += weightReal;
                    //Da o devido peso ao index do guess factor da onda
                    normalSegment[index] += (weightReal / normalSegment[0]);
                    //Faz a mesma coisa para a segmentacao rapida
                    fastSegment[0] += weightReal;
                    fastSegment[index] += (weightReal / fastSegment[0]);

                    return true;
                }
                lastTime++;
                lastEnemyPosition.setLocation(lastEnemyPosition.getX() + deltaX, lastEnemyPosition.getY() + deltaY);
            }
            while (lastTime < time);
            return false;
        }
    }
}

