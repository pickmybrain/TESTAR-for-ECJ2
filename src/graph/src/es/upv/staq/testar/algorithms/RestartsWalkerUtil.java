/*****************************************************************************************
 *                                                                                       *
 * COPYRIGHT (2017):                                                                     *
 * Universitat Politecnica de Valencia                                                   *
 * Camino de Vera, s/n                                                                   *
 * 46022 Valencia, Spain                                                                 *
 * www.upv.es                                                                            *
 *                                                                                       * 
 * D I S C L A I M E R:                                                                  *
 * This software has been developed by the Universitat Politecnica de Valencia (UPV)     *
 * in the context of the TESTAR development team (www.testar.org):                       *
 * This graph project is distributed FREE of charge under the TESTAR license, as an open *
 * source project under the BSD3 licence (http://opensource.org/licenses/BSD-3-Clause)   *
 *                                                                                       * 
 *****************************************************************************************/

package es.upv.staq.testar.algorithms;

import java.util.Arrays;
import java.util.List;

import org.fruit.alayer.State;

import es.upv.staq.testar.graph.IEnvironment;
import es.upv.staq.testar.graph.IGraphAction;
import es.upv.staq.testar.graph.IGraphState;

/**
 * Utility class for periodic state restarts.
 *
 * @author Urko Rueda Molina (alias: urueda)
 *
 */
public class RestartsWalkerUtil {
		
	private static int TEST_SEQUENCE_LENGTH = 0,
					   sequenceIdx = 0,
					   RESTART_INTERVAL = Integer.MAX_VALUE, // # executed actions
					   RESTART_WINDOW_SCALE = 1,
					   restartCounter = 1, // RESTART_INTERVAL #actions before a RESTART
				  	   restartWindowCounter = -1; // #actions during a RESTART (should depend on pathTargetState)
	
	private static List<IGraphState> pathTargetState = null;
	
	private static boolean forcingStateRestart = false;
	
	public static void setTestSequenceLength(int testSequenceLength){
		TEST_SEQUENCE_LENGTH = testSequenceLength;
		sequenceIdx = 0;
	}
	
	public static int getTestSquenceIdx(){
		return sequenceIdx;
	}
	
	private static void setRestartConfig(IEnvironment env){
		if (TEST_SEQUENCE_LENGTH == 0){ // not initialized?
			RESTART_INTERVAL = Integer.MAX_VALUE;
			RESTART_WINDOW_SCALE = 1;
			System.out.println("-------- DEBUG> " + "TEST_SEQUENCE_LENGTH is 0");
			return;
		}

		final int THRESHOLD = 100; // #actions

		int pendingLength = TEST_SEQUENCE_LENGTH - sequenceIdx;
		if (pendingLength < THRESHOLD / 2){
			RESTART_INTERVAL = Integer.MAX_VALUE; // prevent restarts
			RESTART_WINDOW_SCALE = 1;
			System.out.println("-------- DEBUG> " + "pendingLength <" + pendingLength + "> less than half threshold <" + (THRESHOLD/2) + ">");
			return;
		}
		
		int graphSize = env.getGraphStates().size() * env.getGraphActions().size();
		if (graphSize == 0){
			RESTART_INTERVAL = THRESHOLD / 4;
			RESTART_WINDOW_SCALE = 2;
			System.out.println("-------- DEBUG> " + "graphSize is 0");
			return;
		}
				
		int longestPath = env.getLongestPathLength();
		if (pendingLength < longestPath * 2){
			RESTART_INTERVAL = Integer.MAX_VALUE; // prevent restarts
			RESTART_WINDOW_SCALE = 1;
			System.out.println("-------- DEBUG> " + "pendingLength <" + pendingLength + "> less than double longest path <" + (longestPath * 2) + ">");
			return;
		}
		
		if (sequenceIdx == 0){
			RESTART_INTERVAL = 1; restartCounter = RESTART_INTERVAL; // force restart
			RESTART_WINDOW_SCALE = (int) Math.log((double)longestPath + 1.0d);
			System.out.println("-------- DEBUG> " + "sequenceIdx is 0");
			return;
		}

		RESTART_INTERVAL = pendingLength / (pendingLength / (longestPath * 2));
		if (RESTART_INTERVAL < THRESHOLD * 2)
			RESTART_INTERVAL = THRESHOLD * 2;
		RESTART_WINDOW_SCALE = (int) Math.log((double)longestPath + 1.0d);;
		
		System.out.println("-------- DEBUG> " + "RESTART_INTERVAL <" + RESTART_INTERVAL + "> and RESTART_WINDOW_SCALE <" + RESTART_WINDOW_SCALE + ">");

	}

	/**
	 *
	 * @param walker
	 * @param env
	 * @param state
	 * @param actions
	 * @param jipWrapper
	 * @return 'true' if a restart is feasible, 'false' otherwise
	 */
	public static boolean forceStateRestart(IWalker walker, IEnvironment env, State state){
		if (forcingStateRestart)
			return true;
		restartCounter = 1;
		pathTargetState = WalkerUtil.getPathToLessExploredState(walker,env,state);
		if (pathTargetState != null && !pathTargetState.isEmpty()){
			forcingStateRestart = true;
			restartWindowCounter = 1;
			debug(0);
		} else
			restartWindowCounter = -1;
		return (restartWindowCounter != -1);		
	}
	
	/**
	 * 
	 * @param env
	 * @param state
	 * @return 'true' if a restart must happen, 'false' otherwise
	 */
	public static boolean notifyActionSelection(IWalker walker, IEnvironment env, State state) {
		setRestartConfig(env);
		if (restartWindowCounter == -1){ // no restart
			if (restartCounter >= RESTART_INTERVAL)
				forceStateRestart(walker, env,state);
			else
				restartCounter++;
		} else{ // restart
			if (restartWindowCounter >= (pathTargetState.size() * RESTART_WINDOW_SCALE)){
				restartWindowCounter = -1;
				forcingStateRestart = false;
				if (pathTargetState != null)
					pathTargetState.clear();
				pathTargetState = null;
			} else
				restartWindowCounter++;
		}
		sequenceIdx++;
		return (restartWindowCounter != -1);
	}

	public static void notifyRewardCalculation(IEnvironment env, IGraphAction action) {
		if (pathTargetState != null){
						
			IGraphState[] targetStates = env.getTargetStates(action);
			if (targetStates != null &&
			    Arrays.stream(targetStates).anyMatch(pathTargetState.get(pathTargetState.size() - 1)::equals)){ // path target state
			
				pathTargetState.clear(); // target state reached!
				pathTargetState = null;
				restartWindowCounter = -1;
				System.out.println("[RestartsWalkerUtil] Walker restart successful!");

			}
		}
	}

	public static double getTargetReward(IEnvironment env, IGraphState targetState){
		if (pathTargetState != null && !pathTargetState.isEmpty() && pathTargetState.contains(targetState)){
			int idx = pathTargetState.indexOf(targetState);
			debug(idx);
			return Double.MAX_VALUE / Math.pow(2, pathTargetState.size() - 1 - idx);
		}
		return Double.MIN_VALUE; // should use the algorithm getTargetReward
	}
	
	private static void debug(int idx){
		System.out.print("[RestartsWalkerUtil] RESTART PATH TO TARGET STATE (" + idx + "/" + pathTargetState.size() + "): [F|" +
						 pathTargetState.get(0).getConcreteID() + "] "); // from state
		for (int i=1; i<pathTargetState.size() - 1; i++)
			System.out.print(pathTargetState.get(i).getConcreteID() + " ");
		System.out.print(" [T|" + pathTargetState.get(pathTargetState.size() - 1).getConcreteID() + "]\n"); // target state
	}
	
}
