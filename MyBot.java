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

abstract class Goal {
    public Pilot pilot;

    Goal (Pilot pilot){
        this.pilot = pilot;
    }

    abstract Move update (GameMap gameMap);
}

class Strategy {
    public Goal currentGoal;
    void dispatchException (String exception){
    }

    Goal getCurrentGoal (){
        return currentGoal;
    }
}


class GoToPlanetGoal extends Goal{
    public Planet target;
    public float vicinity;

    GoToPlanetGoal (Pilot pilot, Planet target, float vicinity){
        super(pilot);

        this.target = target;
    }

    @Override
    Move update (GameMap gameMap){
        int playerId = gameMap.getMyPlayerId ();
        Ship ship = gameMap.getShip (playerId, pilot.shipId);
        return Navigation.navigateShipTowardsTarget (gameMap, ship, target, Constants.MAX_SPEED, true, 90, Math.PI / 180.f * 3.0f);
    }
}

class Pilot {
    public int shipId;
    public Goal goal;

    public Pilot (GameMap gameMap, int shipId){
        Planet target = gameMap.getAllPlanets ().values().iterator().next ();
        Goal goal = new GoToPlanetGoal (this, target, 10.0f);
        this.shipId = shipId;
        this.goal = goal;
        Log.log ("Constructing pilot for ship " + shipId);
    }

    public void die (){
        Log.log ("Destructing pilot for ship " + shipId);
    }

    public Move update (GameMap gameMap){
        return goal.update(gameMap);
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
        for (int shipId : pilotShipIds){
            if (!shipIds.contains (shipId)){
                Pilot pilot = pilotsMap.get (shipId);
                pilot.die();
                pilotsMap.remove (shipId);
            }
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
