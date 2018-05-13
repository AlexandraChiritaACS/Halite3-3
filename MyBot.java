import hlt.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Set;
import java.io.StringWriter;
import java.io.PrintWriter;
import hlt.Move;

class Intel {

    public boolean alreadySend(Map<Integer, Pilot> pilotMap, GameMap gameMap, int planetId)
    {
        for(Map.Entry<Integer, Pilot> entry : pilotMap.entrySet())
        {
            Goal goal = entry.getValue().goal;
            if((goal instanceof GoMineGoal) && (((GoMineGoal)goal).planetId == planetId))
            {
                return true;
            }
        }
        return false;
    }

    public int getMiningPlanet (GameMap gameMap, Map<Integer, Pilot> pilotMap, Pilot pilot)
    {
        List<Integer> naveAdverse = new ArrayList<>();
        double minim = 9999999;
        int nearestPlanetId = -1;
        for(final Planet planet : gameMap.getAllPlanets().values())
        {

            if(alreadySend(pilotMap, gameMap, planet.getId()) == true)
            {
                continue;
            }

            if(planet.isFull())
            {
                continue;
            }
        
            
            naveAdverse = planet.getDockedShips();
            Set<Integer> idNave = gameMap.getMyPlayer().getShips().keySet();

            if(idNave.contains(naveAdverse.get(0)))
            {
                continue;
            }
        
    
            double distance = pilot.getShip(gameMap).getDistanceTo(planet);
            if(distance < minim)
            {
                minim = distance;
                nearestPlanetId = planet.getId();
            }
            
            
        }

        return nearestPlanetId;
    }
}




abstract class Goal {
    public Task currentTask;

    public abstract Task taskCompleted (Task task);
    public abstract Task alarm (String issue);

    Task getCurrentTask (){
        return currentTask;
    }
}

class GoMineGoal extends Goal {
    public final transient String GO_TO_PLANET = "GoToPlanet";
    public final transient String DOCK_PLANET = "DockPlanet";
    public int planetId;
    public GoToPlanetTask gotoPlanetTask;
    public DockPlanetTask dockPlanetTask;

    public GoMineGoal (GameMap gameMap, Pilot pilot, int planetId){
        this.planetId = planetId;
        double planetRadius = gameMap.getPlanet (planetId).getRadius ();
        dockPlanetTask = new DockPlanetTask (DOCK_PLANET, pilot, this, planetId);
        gotoPlanetTask = new GoToPlanetTask (GO_TO_PLANET, pilot, this, planetRadius + 1.0, planetId);
        currentTask = gotoPlanetTask;
    }

    @Override
    public Task alarm(String issue) {
        return currentTask;
    }

    @Override
    public Task taskCompleted(Task task) {
        switch (task.name){
            case GO_TO_PLANET:{
                currentTask = dockPlanetTask;
                return currentTask;
            }
            case DOCK_PLANET:{
                return null;
            }
        }
        return currentTask;
    }
}

abstract class Task {
    public String name;
    public Pilot pilot;
    public Goal goal;

    Task (String name, Pilot pilot, Goal goal){
        this.name = name;
        this.pilot = pilot;
        this.goal = goal;
    }

    abstract Move update (GameMap gameMap);
}

abstract class GoToTask extends Task{
    public static transient final String ISSUE_NO_TARGET = "NoTarget";
    public double radius;

    GoToTask(String name, Pilot pilot, Goal goal, double radius) {
        super(name, pilot, goal);
        this.radius = radius;
    }

    abstract Position getTarget (GameMap gameMap);

    @Override
    public Move update (GameMap gameMap){
        int playerId = gameMap.getMyPlayerId ();
        Ship ship = gameMap.getShip (playerId, pilot.shipId);
        Position target = getTarget(gameMap);
        if (target == null){
            return goal.alarm(ISSUE_NO_TARGET).update(gameMap);
        }
        double distance = ship.getDistanceTo (target);
        if (distance <= radius){
            Task newTask = goal.taskCompleted(this);
            return newTask != null ? newTask.update(gameMap) : null;
        }
        int speed = Constants.MAX_SPEED;
        if (distance - (double)speed < radius){
            speed = (int)Math.ceil ((distance - radius));
        }
        return Navigation.navigateShipTowardsTarget (gameMap, ship, target, speed, true, 90, Math.PI / 180.f * 3.0f);
    }
}

class GoToPlanetTask extends GoToTask{
    public int planetId;

    GoToPlanetTask (String name, Pilot pilot, Goal goal, double radius, int planetId){
        super(name, pilot, goal, radius);

        this.planetId = planetId;
    }

    @Override
    Position getTarget(GameMap gameMap) {
        return gameMap.getPlanet (planetId);
    }
}

class DockPlanetTask extends Task {
    public int planetId;
    public int numUpdates;

    DockPlanetTask(String name, Pilot pilot, Goal goal, int planetId) {
        super(name, pilot, goal);
    }

    @Override
    Move update(GameMap gameMap) {
        int playerId = gameMap.getMyPlayerId ();
        Ship ship = gameMap.getShip (playerId, pilot.shipId);
        Planet planet = gameMap.getPlanet (planetId);
        numUpdates ++;
        if (numUpdates > 5){
            Task newTask = goal.taskCompleted(this);
            return newTask != null ? newTask.update(gameMap) : null;
        }
        return new DockMove (ship, planet);
    }
}

class Pilot {
    public int shipId;
    public Goal goal;
    public static int count;

    public Pilot (GameMap gameMap, int shipId){
        this.shipId = shipId;
        int planetId = gameMap.getAllPlanets ().keySet ().iterator().next();
        for (int i = 0; i < count; ++i)
            planetId = gameMap.getAllPlanets ().keySet ().iterator().next();
        count ++;
        goal = new GoMineGoal (gameMap, this, planetId);
        Log.log ("Constructing pilot for ship " + shipId);
    }

    public Ship getShip (GameMap gameMap){
        Player myPlayer = gameMap.getMyPlayer ();
        return myPlayer.getShip (shipId);
    }

    public void die (){
        Log.log ("Destructing pilot for ship " + shipId);
    }

    public Move update (GameMap gameMap){
        Task task = goal.getCurrentTask();
        if (task != null)
            return task.update(gameMap);
        else
            return null;
    }

}

public class MyBot {
    static Map<Integer, Pilot> pilotsMap = new HashMap <>();

    public static void update (GameMap gameMap, List<Move> outMoves){
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
                Pilot pilot = new Pilot (gameMap, shipId);
                pilotsMap.put (shipId, pilot);
            }
        }
        // kill dead pilotsMap (Blue skies)
        List <Integer> deadPilots = new ArrayList<>();
        for (int shipId : pilotShipIds){
            if (!shipIds.contains (shipId)){
                Pilot pilot = pilotsMap.get (shipId);
                pilot.die();
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
            Move move = pilot.update(gameMap);
            if (move != null){
                outMoves.add (move);
            }
        }
    }

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
        for (;;) {
            networking.updateMap(gameMap);

            try {
                update(gameMap, moves);
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
