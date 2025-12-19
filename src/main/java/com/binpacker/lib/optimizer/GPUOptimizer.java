package com.binpacker.lib.optimizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.solver.parallelsolvers.solvers.GPUSolver;
import com.binpacker.lib.solver.parallelsolvers.solvers.ParallelSolverInterface;
import com.binpacker.lib.solver.parallelsolvers.solvers.ReferenceSolver;

public class GPUOptimizer extends Optimizer<ParallelSolverInterface> {

	private ReferenceSolver referenceSolver;

	@Override
	protected List<Solution> evaluatePopulation(List<List<Integer>> population) {
		// Get reference solver from GPUSolver if available
		if (referenceSolver == null && solverSource instanceof GPUSolver) {
			referenceSolver = ((GPUSolver) solverSource).getReferenceSolver();
		}

		// Use the GPU solver to get scores for all orders in parallel
		List<Integer> scores = solverSource.solve(boxes, population);

		List<Solution> scored = new ArrayList<>();
		for (int i = 0; i < population.size(); i++) {
			List<Integer> order = population.get(i);
			int volumeScore = scores.get(i);

			double finalScore = 0;

			if (growingBin) {

				finalScore = volumeScore;
			} else {
				finalScore = volumeScore;
			}

			// We pass null for 'solved' because we don't have the boxes yet.
			scored.add(new Solution(order, finalScore, null));
		}

		return scored;
	}

	@Override
	protected List<List<Box>> finalizeBestSolution(Solution bestSolution) {
		// Reconstruct the full solution using CPU reference
		List<Bin> packedBins = referenceSolver.solve(boxes, bestSolution.order, bin);

		List<List<Box>> result = new ArrayList<>();
		for (Bin b : packedBins) {
			result.add(b.boxes);
		}

		return result;
	}

	@Override
	public double rate(List<List<Box>> solution, Bin bin) {

		if (solution.size() <= 1) {
			return 0.0;
		}
		double totalVolume = solution.subList(0, solution.size() - 1).stream()
				.flatMap(List::stream)
				.mapToDouble(Box::getVolume)
				.sum();
		return totalVolume / ((solution.size() - 1) * bin.getVolume());

	}

}
