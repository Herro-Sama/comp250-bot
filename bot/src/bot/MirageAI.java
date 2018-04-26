/* 
 * To change this template, choose Tools | Templates
 * and open the template in the editor. 
 */
package bot;

import ai.abstraction.AbstractAction;
import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Harvest;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.ParameterSpecification;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.units.*;

/**
 *
 * @author Original Author Cristiano D'Angelo, Edited by Herro-Sama(1507729)
 *  
 * 
 */
public class MirageAI extends AbstractionLayerAI {

    Random r = new Random();
    protected UnitTypeTable utt;
    UnitType workerType;
    UnitType baseType;
    UnitType barracksType;
    UnitType rangedType;
    UnitType heavyType;
    UnitType lightType;
    boolean buildingRacks = false;
    int resourcesUsed = 0;
    boolean isRush = false;

    public MirageAI(UnitTypeTable a_utt) 
	{
        this(a_utt, new AStarPathFinding());
    }

    public MirageAI(UnitTypeTable a_utt, PathFinding a_pf) 
	{
        super(a_pf);
        reset(a_utt);
    }

    public void reset() 
	{
        super.reset();
    }

    public void reset(UnitTypeTable a_utt) 
	{
        utt = a_utt;
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        barracksType = utt.getUnitType("Barracks");
        rangedType = utt.getUnitType("Ranged");
        lightType = utt.getUnitType("Light");
    }

    public AI clone() 
	{
        return new MirageAI(utt, pf);
    }

	/* 
	
	This is the main loop for this bot, this is called once for every cycle and has to evaluate certain conditions.
	It currently chooses what actions it should take in order to win the match.
	
	*/
    public PlayerAction getAction(int player, GameState gs) 
	{
		//Set the PhysicalGameState for future use later.
        PhysicalGameState pgs = gs.getPhysicalGameState();
        // Set which player the bot is so for use later.
		Player p = gs.getPlayer(player);

		//Check if the map is particularly small and prepare a rush strategy.
        if ((pgs.getWidth() * pgs.getHeight()) <= 144) 
		{
            isRush = true;
        }

		// Create a list of workers and populate it with all the starting workers this map provides.
        List<Unit> workers = new LinkedList<Unit>();
        for (Unit u : pgs.getUnits()) 
		{
            if (u.getType().canHarvest && u.getPlayer() == player) 
			{
                workers.add(u);
            }
        }

		//Give the available workers their behaviours depending on is a rush strategy is being deployed.
        if (isRush) 
		{
            rushWorkersBehavior(workers, p, pgs, gs);
        } 
		
		else 
		{
            workersBehavior(workers, p, pgs, gs);
        }

        // give all of the bases their actions based on the current strategy.
        for (Unit u : pgs.getUnits()) 
		{
            if (u.getType() == baseType && u.getPlayer() == player && gs.getActionAssignment(u) == null) 
			{
                if (isRush) 
				{
                    rushBaseBehavior(u, p, pgs);
                } 
				else 
				{
                    baseBehavior(u, p, pgs, gs);
                }
            }
        }

        // Set the barracks behaviours.
        for (Unit u : pgs.getUnits()) 
		{
            if (u.getType() == barracksType && u.getPlayer() == player && gs.getActionAssignment(u) == null) 
			{
                barracksBehavior(u, p, pgs);
            }
        }

        // Set the behaviour for units based on if they are a melee or rangedUnit.
        for (Unit u : pgs.getUnits()) 
		{
            if (u.getType().canAttack && !u.getType().canHarvest && u.getPlayer() == player && gs.getActionAssignment(u) == null) 
			{
                if (u.getType() == rangedType) 
				{
                    rangedUnitBehavior(u, p, gs);
                } 
				else 
				{
                    meleeUnitBehavior(u, p, gs);
                }
            }
        }

		// Send the action after it's been changed to a usable format.
        return translateActions(player, gs);
    }

	/*

	This is where the bases choose evaluate their moves and begins carrying out their actions.

	*/

    public void baseBehavior(Unit u, Player p, PhysicalGameState pgs, GameState gs) {

        int nbases = 0;
        int nbarracks = 0;
        int nworkers = 0;
        int nranged = 0;
        int resources = p.getResources();


		// Check which units belong to the bot and keep track of them.
        for (Unit u2 : pgs.getUnits()) 
		{
            if (u2.getType() == workerType && u2.getPlayer() == p.getID()) 
			{
                nworkers++;
            }

            if (u2.getType() == barracksType && u2.getPlayer() == p.getID()) 
			{
                nbarracks++;
            }

            if (u2.getType() == baseType && u2.getPlayer() == p.getID()) 
			{
                nbases++;
            }

            if (u2.getType() == rangedType && u2.getPlayer() == p.getID()) 
			{
                nranged++;
            }
        }

		// Check if the number of workers and if we need to begin building more of them.
        if ((nworkers < (nbases + 1) && p.getResources() >= workerType.cost) || nranged > 6) 
		{
            train(u, workerType);
        }

        //Buffers the resources that are being used for barracks.
        if (resourcesUsed != barracksType.cost * nbarracks) 
		{
            resources = resources - barracksType.cost;
        }


		// If there is an excess of resources train workers.
        if (buildingRacks && (resources >= workerType.cost + rangedType.cost)) 
		{
            train(u, workerType);
        }
    }

	/*

	This is the barracks behaviour which is to check if they can afford rangedUnits and build them.

	*/
    public void barracksBehavior(Unit u, Player p, PhysicalGameState pgs) 
	{
		
		//Check if the player can build a ranged Unit.
        if (p.getResources() >= rangedType.cost) 
		{
            train(u, rangedType);
        }
    }


	/*

	This script is for handling any melee units, and workers not currently harvesting.

	*/
    public void meleeUnitBehavior(Unit u, Player p, GameState gs) 
	{
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        Unit closestRacks = null;
        Unit closestBase = null;
        Unit closestEnemyBase = null;
        int closestDistance = 0;

		//Check all units on the board to find enemies and then find the closest.
        for (Unit u2 : pgs.getUnits()) 
		{
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) 
			{
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                
				if (closestEnemy == null || d < closestDistance) 
				{
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }

			// Get any barracks buildings the bot has and find the closest.
            if (u2.getType() == barracksType && u2.getPlayer() == p.getID()) 
			{
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                
				if (closestRacks == null || d < closestDistance) 
				{
                    closestRacks = u2;
                    closestDistance = d;
                }
            }

			// Get the nearest friendly base.
            if (u2.getType() == baseType && u2.getPlayer() == p.getID()) 
			{
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                
				if (closestBase == null || d < closestDistance) 
				{
                    closestBase = u2;
                    closestDistance = d;
                }
            }

			// Get the nearest enemy base.
            if (u2.getType() == baseType && u2.getPlayer() != p.getID()) {
                
				int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                
				if (closestEnemyBase == null || d < closestDistance) 
				{
                    closestEnemyBase = u2;
                    closestDistance = d;
                }
            }
        }

        if (closestEnemy != null) 
		{
			// If the enemy has no units and the time is less than 400 attack.
            if (gs.getTime() < 400 || isRush) 
			{
                attack(u, closestEnemy);
            }
			
			else 
			{
				// Calls the external library of rangedTactics to resolve the units actions.
                rangedTactic(u, closestEnemy, closestBase, closestEnemyBase, utt, p);
            }
        }
    }

	/*

	This is ranged unit behaviours which will control any bot controlled ranged units.

	*/
    public void rangedUnitBehavior(Unit u, Player p, GameState gs) 
	{
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        Unit closestRacks = null;
        Unit closestBase = null;
        Unit closestEnemyBase = null;
        int closestDistance = 0;

        
        //Checks all units currently in the game state.
        for (Unit u2 : pgs.getUnits()) 
        {
        	//Check to see which player the Unit belongs to.
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) 
            {
            	// If the Unit doesn't belong to the player get its distance.
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                
                if (closestEnemy == null || d < closestDistance) 
                {
                	// Store the current shortest distance.
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
            
            // Check if the unit is a base and what player it belongs too.
            if (u2.getType() == baseType && u2.getPlayer() == p.getID()) 
            {
            	//Get the absolute distance of the base.
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                
                if (closestBase == null || d < closestDistance) 
                {
                	// If this is the closest base set it.
                    closestBase = u2;
                    closestDistance = d;
                }
            }

            // Check if the unit is a barracks and who it belongs too.
            if (u2.getType() == barracksType && u2.getPlayer() == p.getID()) 
            {
            	// Get the distance to the barracks
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                
                if (closestRacks == null || d < closestDistance) 
                {
                	// if this is the closest barracks set it to be the closest barracks.
                    closestRacks = u2;
                    closestDistance = d;
                }
            }
            
            //Get the closest enemy base
            if (u2.getType() == baseType && u2.getPlayer() != p.getID()) 
            {
                // Get the distance to the base
            	int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                
                if (closestEnemyBase == null || d < closestDistance) 
                {
                	// If this is the closest base store it's details.
                    closestEnemyBase = u2;
                    closestDistance = d;
                }
            }
        }
        
        if (closestEnemy != null) 
        {
        	// If closest enemy isn't null begin running ranged tactics to resolve actions.
            rangedTactic(u, closestEnemy, closestBase, closestEnemyBase, utt, p);

        }
    }

    public void workersBehavior(List<Unit> workers, Player p, PhysicalGameState pgs, GameState gs) {
        int nbases = 0;
        int nbarracks = 0;
        int nworkers = 0;
        resourcesUsed = 0;
        int resourceNodes = 0;

        List<Unit> freeWorkers = new LinkedList<Unit>();
        List<Unit> battleWorkers = new LinkedList<Unit>();


		// Sort through each unit to find all the bases and workers controlled by the player during this cycle.
        for (Unit u2 : pgs.getUnits()) 
        {
            if (u2.getType() == baseType && u2.getPlayer() == p.getID()) 
            {
                nbases++;
            }
            
            if (u2.getType() == barracksType && u2.getPlayer() == p.getID()) 
            {
                nbarracks++;
            }
            
            if (u2.getType() == workerType && u2.getPlayer() == p.getID()) 
            {
                nworkers++;
            }
        }

		//Check the number of workers and give them their work assignment groups.
        if (workers.size() > (nbases + 2) ) 
        {
            for (int n = 0; n < (nbases + 2); n++) 
            {
                freeWorkers.add(workers.get(0));
                workers.remove(0);
            }
            
            battleWorkers.addAll(workers);
        } 
        
		// If there aren't enough workers assign all available workers to freeworkers.
        else 
        {
            freeWorkers.addAll(workers);
        }

        if (workers.isEmpty()) 
        {
            return;
        }

        List<Integer> reservedPositions = new LinkedList<Integer>();
        if (nbases == 0 && !freeWorkers.isEmpty()) 
		{
            // build a base:
            if (p.getResources() >= baseType.cost) 
			{
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u, baseType, u.getX(), u.getY(), reservedPositions, p, pgs);
            }
        }
        
		if ((nbarracks == 0) && (!freeWorkers.isEmpty()) && nworkers > 1 && p.getResources() >= barracksType.cost) 
		{
            //The problem with this right now is that we can only track when a build command is sent
            //Not when it actually starts building the building.
            Unit u = freeWorkers.remove(0);
            buildIfNotAlreadyBuilding(u, barracksType, u.getX(), u.getY(), reservedPositions, p, pgs);
            resourcesUsed += barracksType.cost;
            buildingRacks = true;

        } 
		
		else 
		{
            resourcesUsed = barracksType.cost * nbarracks;
        }

        if (nbarracks > 1) 
		{
            buildingRacks = true;
        }

        // Make all battleWorkers perform like meleeUnits
        for (Unit u : battleWorkers) 
		{
            meleeUnitBehavior(u, p, gs);
    	}

        // harvest with all the free workers:
        for (Unit u : freeWorkers) 
		{
            Unit closestBase = null;
            Unit closestResource = null;
            Unit closestEnemyBase = null;
            int closestDistance = 0;

            // Go through all units to find resources.
            for (Unit u2 : pgs.getUnits()) 
			{
            	
                if (u2.getType().isResource) 
				{
                	resourceNodes++;
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    
                    // Check if the current resource is the closest and store it.
                    if (closestResource == null || d < closestDistance) 
					{
                        closestResource = u2;
                        closestDistance = d;
                    }
                   
                }
                
                // Go through all units and get the closest enemy base.
                if (u2.getType() == baseType && u2.getPlayer() != p.getID()) 
				{
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());

                    // If this is the closest enemy base then store its location.
                    if (closestEnemyBase == null || d < closestDistance) 
					{
                        closestEnemyBase = u2;
                        closestDistance = d;
                    }
                }
            }
            
            // Reset the closest distance for the next pass.
			closestDistance = 0;

			// Get the nearest friendly base and and make note of it's location.
            for (Unit u2 : pgs.getUnits()) 
			{
            	
                if (u2.getType().isStockpile && u2.getPlayer() == p.getID()) 
				{
                    
					int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    
					if (closestBase == null || d < closestDistance) 
					{
                        closestBase = u2;
                        closestDistance = d;
                    }
                }
            }
		            
            if (resourceNodes < 8)
            {
		        if (closestResource == null || distance(closestResource, closestEnemyBase) < distance(closestResource, closestBase)) 
				{
		        	//Do Nothing
				}
		        
		        else 
		        {
	                 if (closestResource != null && closestBase != null) 
	                 {
	                     AbstractAction aa = getAbstractAction(u);
	                     if (aa instanceof Harvest) 
	                     {
	                         Harvest h_aa = (Harvest) aa;
	 
	                         if (h_aa.getTarget() != closestResource || h_aa.getBase() != closestBase) 
	                         {
	                             harvest(u, closestResource, closestBase);
	                         }
	                     } 
	                     
	                     else 
	                     {
	                         harvest(u, closestResource, closestBase);
	                     }
	                 }
		        }
            }
            
            else
            {
            	if (closestResource != null && closestBase != null) 
                {
            		for (Unit u2 : pgs.getUnits()) 
        			{
                    	
                        if (u2.getType().isResource) 
        				{
                            int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                            
                            // Check if the current resource is the closest and store it.
                            if (closestResource == null || d < closestDistance) 
        					{
                                closestResource = u2;
                                closestDistance = d;
                            }
                           
                        }
	                    AbstractAction aa = getAbstractAction(u);
	                    if (aa instanceof Harvest) 
	                    {
	                        Harvest h_aa = (Harvest) aa;
	
	                        if (h_aa.getTarget() != closestResource || h_aa.getBase() != closestBase) 
	                        {
	                            harvest(u, closestResource, closestBase);
	                        }
	                    } 
	                    
	                    else 
	                    {
	                        harvest(u, closestResource, closestBase);
	                    }
        			}
                }
            	
            }
            
		}
    }

    public void rushBaseBehavior(Unit u, Player p, PhysicalGameState pgs) 
	{
        if (p.getResources() >= workerType.cost) 
		{
            train(u, workerType);
        }
    }

    public void rushWorkersBehavior(List<Unit> workers, Player p, PhysicalGameState pgs, GameState gs) 
	{
        int nbases = 0;
        int nworkers = 0;
        resourcesUsed = 0;

        List<Unit> freeWorkers = new LinkedList<Unit>();
        List<Unit> battleWorkers = new LinkedList<Unit>();

        for (Unit u2 : pgs.getUnits()) 
		{
            if (u2.getType() == baseType && u2.getPlayer() == p.getID()) 
			{
                nbases++;
            }
            
			if (u2.getType() == workerType && u2.getPlayer() == p.getID()) 
			{
                nworkers++;
            }
        }

        if (p.getResources() == 0) 
		{
            battleWorkers.addAll(workers);
        } 
		
		else if (workers.size() > (nbases)) 
		{
            for (int n = 0; n < (nbases); n++) 
			{
                freeWorkers.add(workers.get(0));
                workers.remove(0);
            }
            battleWorkers.addAll(workers);
        } 
		
		else 
		{
            freeWorkers.addAll(workers);
        }

        if (workers.isEmpty()) 
		{
            return;
        }

        List<Integer> reservedPositions = new LinkedList<Integer>();
        if (nbases == 0 && !freeWorkers.isEmpty()) 
		{
            // build a base:
            if (p.getResources() >= baseType.cost) 
			{
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u, baseType, u.getX(), u.getY(), reservedPositions, p, pgs);
            }
        }

        for (Unit u : battleWorkers) 
		{
            meleeUnitBehavior(u, p, gs);
        }

        // harvest with all the free workers:
        for (Unit u : freeWorkers) 
		{
            Unit closestBase = null;
            Unit closestResource = null;
            int closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) 
			{
                if (u2.getType().isResource) 
				{
                    
					int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    
					if (closestResource == null || d < closestDistance) 
					{
                        closestResource = u2;
                        closestDistance = d;
                    }
                }
            }
            closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) 
			{
                if (u2.getType().isStockpile && u2.getPlayer() == p.getID()) 
				{
                    
					int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    
					if (closestBase == null || d < closestDistance) 
					{
                        closestBase = u2;
                        closestDistance = d;
                    }
                }
            }

            if (closestResource != null && closestBase != null) 
			{
                AbstractAction aa = getAbstractAction(u);
                if (aa instanceof Harvest) 
				{
                    Harvest h_aa = (Harvest) aa;

                    if (h_aa.getTarget() != closestResource || h_aa.getBase() != closestBase) 
					{
                        harvest(u, closestResource, closestBase);
                    }

                } 
				
				else 
				{
                    harvest(u, closestResource, closestBase);
                }
            }
        }
    }

    public void rangedTactic(Unit u, Unit target, Unit home, Unit enemyBase, UnitTypeTable utt, Player p) 
    {
        actions.put(u, new CRanged_Tactic(u, target, home, enemyBase, pf, utt, p));
    }

    //Calculates distance between unit a and unit b
    public double distance(Unit a, Unit b) 
	{
        if (a == null || b == null) 
		{
            return 0.0;
        }

        int dx = b.getX() - a.getX();
        int dy = b.getY() - a.getY();
        double toReturn = Math.sqrt(dx * dx + dy * dy);
        return toReturn;
    }

    @Override
    public List<ParameterSpecification> getParameters() 
	{
        List<ParameterSpecification> parameters = new ArrayList<>();

        parameters.add(new ParameterSpecification("PathFinding", PathFinding.class, new AStarPathFinding()));

        return parameters;
    }
}
