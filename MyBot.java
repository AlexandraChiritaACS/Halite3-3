import hlt.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Set;
import java.util.TreeMap;
import java.io.StringWriter;
import java.io.PrintWriter;
import hlt.Move;

class Util{
    public static int getRandom (int min, int max){
        double r = Math.random ();
        return min + (int)(r * (double)(max - min));
    }
}

class ShipInfo {
        public int shipId;
        public int playerId;

        public ShipInfo() {
            shipId = -1;
            playerId = -1;
        }
    };


class GameManager {
    public Map<Integer, Pilot> pilotsMap = new HashMap <>();
    public int turn;
    public int goals;
    public int firstPlanetId = -1;

    public ShipInfo closestShip(GameMap gameMap, Pilot pilot, boolean onlyDocked)
    {
         ShipInfo ob = new ShipInfo();
         Map<Double, Entity> entityByDistance = new TreeMap<>();
         entityByDistance = gameMap.nearbyEntitiesByDistance(pilot.getShip(gameMap));
         if (onlyDocked){
             for (Map.Entry<Double, Entity> entry : entityByDistance.entrySet()) {
                 if ((entry.getValue() instanceof Ship) && (entry.getValue().getOwner() != gameMap.getMyPlayerId())) {
                     Ship ship = (Ship)entry.getValue();
                     if (ship.getDockingStatus () == Ship.DockingStatus.Undocked)
                         continue;
                     ob.shipId = entry.getValue().getId();
                     ob.playerId = entry.getValue().getOwner();
                     return ob;
                 }
             }
         } else {
             for (Map.Entry<Double, Entity> entry : entityByDistance.entrySet()) {
                 if ((entry.getValue() instanceof Ship) && (entry.getValue().getOwner() != gameMap.getMyPlayerId())) {
                     ob.shipId = entry.getValue().getId();
                     ob.playerId = entry.getValue().getOwner();
                     return ob;
                 }
             }
         }
         
         return ob;
    }


    public int secondClosestPlanet(GameMap gameMap, Pilot pilot)
    {
        int idPlanet = -1;
        Map<Double, Entity> entityByDistance = new TreeMap<>();
        entityByDistance = gameMap.nearbyEntitiesByDistance(pilot.getShip(gameMap));
        for(Map.Entry<Double, Entity> entry : entityByDistance.entrySet())
        {
            if((entry.getValue() instanceof Planet) && (((Planet)entry.getValue()).isFull()))
            {
                continue;
            }

            if((entry.getValue() instanceof Planet) && (!((Planet)entry.getValue()).isOwned()))
            {
                idPlanet = entry.getValue().getId();
                return idPlanet;
            }

            // if((entry.getValue() instanceof Planet) && (entry.getValue().getOwner() == gameMap.getMyPlayerId()) && (!((Planet)entry.getValue()).isFull()))
            // {
            //     idPlanet = entry.getValue().getId();
            //     return idPlanet;
            // }
        }

        for(Map.Entry<Double, Entity> entry : entityByDistance.entrySet())
        {
            if((entry.getValue() instanceof Planet) && (((Planet)entry.getValue()).isFull()))
            {
                continue;
            }

            if((entry.getValue() instanceof Planet) && (entry.getValue().getOwner() == gameMap.getMyPlayerId()) && (!((Planet)entry.getValue()).isFull()))
            {
                idPlanet = entry.getValue().getId();
                return idPlanet;
            }
        }
        

        return idPlanet;
    }


    public void update (GameMap gameMap, List<Move> outMoves){
        turn ++;
        outMoves.clear ();
        Player myPlayer = gameMap.getMyPlayer ();
        //==================
        // Update the pilots
        //==================
        Set<Integer> shipIds = myPlayer.getShips ().keySet ();
        Set<Integer> pilotShipIds = pilotsMap.keySet ();
        // construct new pilotsMap
        for (int shipId : shipIds){
            if(!pilotShipIds.contains (shipId)){
                Pilot pilot = new Pilot (this, gameMap, shipId);
                pilotsMap.put (shipId, pilot);
            }
        }
        // kill dead pilotsMap (Blue skies)
        List <Integer> deadPilots = new ArrayList<>();
        for (int shipId : pilotShipIds){
            if (!shipIds.contains (shipId)){
                Pilot pilot = pilotsMap.get (shipId);
                pilot.die(this, gameMap);
                deadPilots.add (shipId);
            }
        }
        for (int pilotId : deadPilots){
            pilotsMap.remove (pilotId);
        }

        // ========================================
        // update the pilots and generate the moves
        // ========================================
        Collection<Pilot> pilots = pilotsMap.values ();
        for (Pilot pilot : pilots){
            Move move = pilot.update(this, gameMap);
            if (move != null){
                outMoves.add (move);
            }
        }

        processSelfCollision (outMoves);
    }

    public void processSelfCollision (List<Move> moves){
        for (int i = 0; i < moves.size (); i ++){
            Move move = moves.get (i);
            if (!(move instanceof ThrustMove))
                continue;
            ThrustMove thrustMove = (ThrustMove)move;
            Ship ship = move.getShip ();
            double angle = (double)thrustMove.getAngle() * Math.PI / 180.0f;
            double jump = (double)thrustMove.getThrust ();
            double x = ship.getXPos () + Math.cos (angle) * jump;
            double y = ship.getYPos () + Math.sin (angle) * jump;
            Log.log ("angle " + thrustMove.getAngle ());

            for (Move otherMove : moves){
                if (!(otherMove instanceof ThrustMove))
                    continue;
                if (move == otherMove)
                    continue;
                ThrustMove otherThrustMove = (ThrustMove)otherMove;
                Ship otherShip = otherMove.getShip ();
                double otherAngle = (double)otherThrustMove.getAngle() * Math.PI / 180.0f;
                double otherJump = (double)otherThrustMove.getThrust ();
                double otherX = otherShip.getXPos () + Math.cos (otherAngle) * otherJump;
                double otherY = otherShip.getYPos () + Math.sin (otherAngle) * otherJump;

                double dx = (x - otherX);
                double dy = (y - otherY);
                double dist = Math.sqrt (dx * dx + dy * dy);

                if (dist < Constants.SHIP_RADIUS * 6.1){
                    move = new ThrustMove (ship, thrustMove.getAngle (), 0);
                    moves.set (i, move);
                }
            }
        }
    }

    public int getNumSent(GameMap gameMap, int planetId)
    {
        int num = 0;
        for(Map.Entry<Integer, Pilot> entry : pilotsMap.entrySet())
        {
            Goal goal = entry.getValue().goal;
            if(goal.isOnRouteToPlanet(planetId))
            {
                num ++;
            }
        }
        return num;
    }

    public int getMiningPlanet (GameMap gameMap, Pilot pilot)
    {
        double minim = 9999999;
        int nearestPlanetId = -1;
        for(final Planet planet : gameMap.getAllPlanets().values())
        {
            // skip if planet is owned by enemies
            List<Integer> dockedShipIds = planet.getDockedShips();
            Set<Integer> playerShipIds = gameMap.getMyPlayer().getShips().keySet();
            if(dockedShipIds.size () > 0 && !playerShipIds.contains(dockedShipIds.get(0)))
                continue;

            // skip if planet is full
            if(planet.isFull())
                continue;

            // skip if there are no more docking spaces available
            int numSent = getNumSent(gameMap, planet.getId());
            int numDocked = planet.getDockedShips().size ();
            int numDockingSpots = planet.getDockingSpots ();
            if(numSent + numDocked >= numDockingSpots )
                continue;

            double distance = pilot.getShip(gameMap).getDistanceTo(planet);
            if(distance < minim)
            {
                minim = distance;
                nearestPlanetId = planet.getId();
            }

        }

        return nearestPlanetId;
    }

    public Goal getGoal (GameMap gameMap, Pilot pilot){
        goals ++;
        // start of the game
        if (1 == turn){
            if (firstPlanetId == -1)
                firstPlanetId = getMiningPlanet(gameMap, pilot);
            if (goals <= 2){
                Log.log ("pilot for ship " + pilot.shipId + " goal GoMineGoal");
                return new GoMineGoal (this, gameMap, pilot, firstPlanetId);
            }
            Log.log ("pilot for ship " + pilot.shipId + " goal DefentPlanetGoal");
            return new DefentPlanetGoal (this, gameMap, pilot, firstPlanetId);
        }

        // spawned ships
        if (0 == (goals % 3)){
            ShipInfo shipInfo = closestShip(gameMap, pilot, true);
            Log.log ("pilot for ship " + pilot.shipId + " goal GoAttackGoal");
            return new GoAttackGoal (this, gameMap, pilot, shipInfo.playerId, shipInfo.shipId, false);
        } else {
            int planetId = secondClosestPlanet(gameMap, pilot);
            Log.log ("pilot for ship " + pilot.shipId + " goal GoMineGoal");
            return new GoMineGoal(this, gameMap, pilot, planetId);
        }
    }
}

abstract class Goal {
    public Task currentTask;

    public Move taskCompleted (GameManager gameManager, GameMap gameMap) {
        Log.log ("Task " + currentTask.name + " completed");
        return null;
    }

    public Move alarm (GameManager gameManager, GameMap gameMap, String issue){
        Log.log ("Task " + currentTask.name + " alarm " + issue);
        return null;
    }

    abstract public boolean isOnRouteToPlanet (int planetId);

    Move update (GameManager gameManager, GameMap gameMap){
        return currentTask != null ? currentTask.update(gameManager, gameMap) : null;
    }
}

class GoMineGoal extends Goal {
    public static final transient String GO_TO_PLANET = "GoToPlanet";
    public static final transient String DOCK_PLANET = "DockPlanet";
    public int planetId;
    public GoToPlanetTask gotoPlanetTask;
    public DockPlanetTask dockPlanetTask;

    public GoMineGoal (GameManager gameManager, GameMap gameMap, Pilot pilot, int planetId){
        this.planetId = planetId;
        Planet planet = gameMap.getPlanet (planetId);
        double radius = planet.getRadius ();
        gotoPlanetTask = new GoToPlanetTask (GO_TO_PLANET, gameManager, gameMap, pilot, this, planetId, radius + 2.0, true);
        dockPlanetTask = new DockPlanetTask (DOCK_PLANET, gameManager, gameMap, pilot, this, planetId);
        currentTask = gotoPlanetTask;
    }

    @Override
    public Move alarm(GameManager gameManager, GameMap gameMap, String issue) {
        super.alarm(gameManager, gameMap, issue);
        switch (issue){
            case Task.ISSUE_NO_TARGET:{
                Pilot pilot = currentTask.pilot;
                pilot.goal = gameManager.getGoal(gameMap, pilot);
                return pilot.goal.update(gameManager, gameMap);
            }
        }
        return null;
    }

    @Override
    public Move taskCompleted(GameManager gameManager, GameMap gameMap) {
        super.taskCompleted(gameManager, gameMap);
        switch (currentTask.name){
            case GO_TO_PLANET:{
                currentTask = dockPlanetTask;
                return currentTask.update(gameManager, gameMap);
            }
            case DOCK_PLANET:{
                currentTask = null;
                return null;
            }
        }
        return null;
    }

    @Override
    public boolean isOnRouteToPlanet(int planetId) {
        return this.planetId == planetId;
    }
}

class GoAttackGoal extends Goal {
    public static final transient String GO_TO_SHIP = "GoToShip";
    public int playerId;
    public int shipId;
    public boolean kamikaze;
    public GoToShipTask goToShipTask;

    public GoAttackGoal (GameManager gameManager, GameMap gameMap, Pilot pilot, int playerId, int shipId, boolean kamikaze){
        this.playerId = playerId;
        this.shipId = shipId;
        this.kamikaze = kamikaze;
        double radius = 0.0f;
        if (!kamikaze){
            Ship ship = gameMap.getShip (playerId, shipId);
            radius = ship.getRadius () + pilot.getShip(gameMap).getRadius ();
        }
        goToShipTask = new GoToShipTask (GO_TO_SHIP, gameManager, gameMap, pilot, this, radius, playerId, shipId);
        currentTask = goToShipTask;
    }

    @Override
    public Move taskCompleted(GameManager gameManager, GameMap gameMap) {
        super.taskCompleted(gameManager, gameMap);
        return null;
    }

    @Override
    public Move alarm(GameManager gameManager, GameMap gameMap, String issue) {
        super.alarm(gameManager, gameMap, issue);
        Pilot pilot = goToShipTask.pilot;
        switch (issue){
            case GoToTask.ISSUE_NO_TARGET:{
                pilot.goal = gameManager.getGoal(gameMap, pilot);
                return pilot.goal.update(gameManager, gameMap);
            }
        }
        return null;
    }

    @Override
    public boolean isOnRouteToPlanet(int planetId) {
        return false;
    }
}

class DefentPlanetGoal extends Goal {
    public static final transient String GO_TO_PLANET = "GoToPlanet";
    public static final transient String PATROL_PLANET = "GoToPlanet";
    public static final transient String GO_TO_SHIP = "GoToShip";
    int planetId;
    int shipId;
    int playerId;
    GoToPlanetTask goToPlanetTask;
    PatrolPlanetTask patrolPlanetTask;

    DefentPlanetGoal (GameManager gameManager, GameMap gameMap, Pilot pilot, int planetId){
        Planet planet = gameMap.getPlanet (planetId);
        double radius = 20.0;
        goToPlanetTask = new GoToPlanetTask(GO_TO_PLANET, gameManager, gameMap, pilot, this, planetId, planet.getRadius () + radius, false);
        patrolPlanetTask = new PatrolPlanetTask(PATROL_PLANET, gameManager, gameMap, pilot, this, planetId, radius);
        currentTask = goToPlanetTask;
    }

    @Override
    public Move taskCompleted(GameManager gameManager, GameMap gameMap) {
        super.taskCompleted(gameManager, gameMap);
        switch (currentTask.name){
            case GO_TO_PLANET: {
                currentTask = patrolPlanetTask;
                return currentTask.update(gameManager, gameMap);
            }
            default:
                currentTask = null;
                return null;
        }
    }

    @Override
    public Move alarm(GameManager gameManager, GameMap gameMap, String issue) {
        super.alarm(gameManager, gameMap, issue);
        switch (issue){
            case Task.ISSUE_ENEMY_CLOSE:{
                ShipInfo shipInfo = gameManager.closestShip (gameMap, currentTask.pilot, false);
                currentTask = new GoToShipTask (GO_TO_SHIP, gameManager, gameMap, currentTask.pilot, this, Constants.SHIP_RADIUS * 2.0, shipInfo.playerId, shipInfo.shipId);
                return currentTask.update(gameManager, gameMap);
            }
            case Task.ISSUE_NO_TARGET:{
                Pilot pilot = currentTask.pilot;
                switch (currentTask.name){
                    case GO_TO_SHIP:{
                        currentTask = goToPlanetTask;
                        return currentTask.update(gameManager, gameMap);
                    }
                    default:{
                        pilot.goal = gameManager.getGoal(gameMap, pilot);
                        return pilot.goal.update(gameManager, gameMap);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean isOnRouteToPlanet(int planetId) {
        return false;
    }

}

abstract class Task {
    public static transient final String ISSUE_NO_TARGET = "NoTarget";
    public static transient final String ISSUE_ENEMY_CLOSE = "EnemyClose";

    public String name;
    public Pilot pilot;
    public Goal goal;

    Task (String name, GameManager gameManager, GameMap gameMap, Pilot pilot, Goal goal){
        this.name = name;
        this.pilot = pilot;
        this.goal = goal;
    }

    abstract Move update (GameManager gameManager, GameMap gameMap);
}

abstract class GoToTask extends Task{
    public double radius;

    GoToTask(String name, GameManager gameManager, GameMap gameMap, Pilot pilot, Goal goal, double radius) {
        super(name, gameManager, gameMap, pilot, goal);
        this.radius = radius;
    }

    abstract Position getTarget (GameMap gameMap);

    @Override
    public Move update (GameManager gameManager, GameMap gameMap){
        int playerId = gameMap.getMyPlayerId ();
        Ship ship = gameMap.getShip (playerId, pilot.shipId);
        Position target = getTarget(gameMap);
        if (target == null){
            return goal.alarm(gameManager, gameMap, ISSUE_NO_TARGET);
        }
        double distance = ship.getDistanceTo (target);
        if (distance <= radius){
            return goal.taskCompleted(gameManager, gameMap);
        }
        int speed = Math.min ((int)(distance - radius + 1.0), Constants.MAX_SPEED);
        if (distance - (double)speed < radius){
            speed = (int)Math.ceil ((distance - radius));
        }
        return Navigation.navigateShipTowardsTarget (gameMap, ship, target, speed, true, 90, Math.PI / 180.f * 5.0f);
    }
}

class GoToPlanetTask extends GoToTask{
    public int planetId;
    public boolean forDocking;

    GoToPlanetTask (String name, GameManager gameManager, GameMap gameMap, Pilot pilot, Goal goal, int planetId, double radius, boolean forDocking){
        super(name, gameManager, gameMap, pilot, goal, radius);
        this.planetId = planetId;
        this.forDocking = forDocking;
    }

    @Override
    Position getTarget(GameMap gameMap) {
        return gameMap.getPlanet (planetId);
    }

    @java.lang.Override
    public Move update(GameManager gameManager, GameMap gameMap) {

        Planet planet = gameMap.getPlanet (planetId);
        int myPlayerId = gameMap.getMyPlayerId ();
        if (planet.isOwned () && planet.getOwner () != myPlayerId){
            return goal.alarm(gameManager, gameMap, ISSUE_NO_TARGET);
        }

        return super.update(gameManager, gameMap);
    }
}

class GoToShipTask extends GoToTask {
    public int playerId;
    public int shipId;

    GoToShipTask(String name, GameManager gameManager, GameMap gameMap, Pilot pilot, Goal goal, double radius, int playerId, int shipId) {
        super(name, gameManager, gameMap, pilot, goal, radius);
        this.playerId = playerId;
        this.shipId = shipId;
    }

    @Override
    Position getTarget(GameMap gameMap) {
        return gameMap.getShip (playerId, shipId);
    }
}

class DockPlanetTask extends Task {
    public int planetId;
    public int numUpdates;

    DockPlanetTask(String name, GameManager gameManager, GameMap gameMap, Pilot pilot, Goal goal, int planetId) {
        super(name, gameManager, gameMap, pilot, goal);
        this.planetId = planetId;
    }

    @Override
    Move update(GameManager gameManager, GameMap gameMap) {
        int playerId = gameMap.getMyPlayerId ();
        Ship ship = gameMap.getShip (playerId, pilot.shipId);
        Planet planet = gameMap.getPlanet (planetId);
        if (planet.isOwned () && planet.getOwner () != playerId){
            goal.alarm(gameManager, gameMap, ISSUE_NO_TARGET);
        }
        numUpdates ++;
        if (numUpdates > 5){
            return goal.taskCompleted(gameManager, gameMap);
        }
        Log.log ("ship " + pilot.shipId + " canDock to " + playerId + " : " + ship.canDock (planet));
        return new DockMove (ship, planet);
    }
}

class PatrolPlanetTask extends Task {
    int planetId;
    double radius;
    double dir = 10.0;

    PatrolPlanetTask(String name, GameManager gameManager, GameMap gameMap, Pilot pilot, Goal goal, int planetId, double radius) {
        super(name, gameManager, gameMap, pilot, goal);
        this.planetId = planetId;
        this.radius = radius;
    }

    @Override
    Move update(GameManager gameManager, GameMap gameMap) {
        Planet planet = gameMap.getPlanet (planetId);
        if (null == planet){
            return goal.alarm(gameManager, gameMap, ISSUE_NO_TARGET);
        }

        int playerId = gameMap.getMyPlayerId ();
        Ship ship = gameMap.getShip (playerId, pilot.shipId);
        ShipInfo shipInfo = gameManager.closestShip (gameMap, pilot, false);
        if (shipInfo.shipId != -1){
            Ship enemy = gameMap.getShip (shipInfo.playerId, shipInfo.shipId);
            double distance = enemy.getDistanceTo (ship);
            if (distance < 40.0){
                return goal.alarm(gameManager, gameMap, ISSUE_ENEMY_CLOSE);
            }
        }

        int speed = 3;
        double width = (double)gameMap.getWidth ();
        double height = (double)gameMap.getHeight ();
        double angle = (double)(planet.orientTowardsInDeg (ship) + dir) * Math.PI / 180.0f;
        double x = planet.getXPos () + Math.cos (angle) * radius;
        double y = planet.getYPos () + Math.sin (angle) * radius;
        double pading = 4.0;
        if ((x < pading) || (y < pading) || (x >= width - pading) || (y >= height - pading)){
            dir = -dir;
            angle = (double)(planet.orientTowardsInDeg (ship) + dir) * Math.PI / 180.0f;
            x = planet.getXPos () + Math.cos (angle) * radius;
            y = planet.getYPos () + Math.sin (angle) * radius;
        }

        Position target = new Position (x, y);

        return Navigation.navigateShipTowardsTarget (gameMap, ship, target, speed, true, 90, Math.PI / 180.f * 5.0f);
    }
}

class Pilot {
    public int shipId;
    public Goal goal;

    public Pilot (GameManager gameManager, GameMap gameMap, int shipId){
        this.shipId = shipId;

        int planetId = gameManager.getMiningPlanet(gameMap, this);

        goal = gameManager.getGoal(gameMap, this);
        Log.log ("Constructing pilot for ship " + shipId + " with goal " + goal.getClass ().toString ());
    }

    public Ship getShip (GameMap gameMap){
        Player myPlayer = gameMap.getMyPlayer ();
        return myPlayer.getShip (shipId);
    }

    public void die (GameManager gameManager, GameMap gameMap){
        Log.log ("Destructing pilot for ship " + shipId);
    }

    public Move update (GameManager gameManager, GameMap gameMap){
        return goal.update(gameManager, gameMap);
    }
}

public class MyBot {

    public static void main(final String[] args) {
        final Networking networking = new Networking();
        final GameMap gameMap = networking.initialize("Tamagocchi");

        // We now have 1 full minute to analyse the initial map.
        final String initialMapIntelligence =
                "width: " + gameMap.getWidth() +
                "; height: " + gameMap.getHeight() +
                "; players: " + gameMap.getAllPlayers().size() +
                "; planets: " + gameMap.getAllPlanets().size();
        Log.log(initialMapIntelligence);

        List<Move> moves = new ArrayList <>();
        GameManager gameManager = new GameManager();
        for (;;) {
            networking.updateMap(gameMap);

            try {
                gameManager.update(gameMap, moves);
            } catch (Exception e){
                logException(e);
            }

            Networking.sendMoves(moves);
        }
    }

    protected static void logException(Exception e) {
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter( writer );
        e.printStackTrace( printWriter );
        printWriter.flush();
        String stackTrace = writer.toString();
        Log.log ("error:" + e.toString ());
        Log.log (stackTrace);
    }
}
