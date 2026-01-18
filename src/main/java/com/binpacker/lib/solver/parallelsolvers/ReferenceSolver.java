package com.binpacker.lib.solver.parallelsolvers;

import java.util.List;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;

/**
 * Interface for CPU-based reference solvers that reconstruct full packing
 * solutions
 * from box orders. Used by GPUOptimizer to finalize solutions after GPU
 * scoring.
 */
public interface ReferenceSolver {

	/**
	 * Solve the bin packing problem for a given order of boxes.
	 * 
	 * @param boxes       The list of boxes to pack
	 * @param order       The order in which to pack the boxes (indices into boxes
	 *                    list)
	 * @param binTemplate The template bin dimensions
	 * @return List of bins with boxes placed
	 */
	List<Bin> solve(List<Box> boxes, List<Integer> order, com.binpacker.lib.solver.common.SolverProperties properties);
}
