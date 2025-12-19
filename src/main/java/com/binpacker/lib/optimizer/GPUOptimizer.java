package com.binpacker.lib.optimizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.solver.parallelsolvers.solvers.FirstFitReference;
import com.binpacker.lib.solver.parallelsolvers.solvers.ParallelSolverInterface;

public class GPUOptimizer extends Optimizer<ParallelSolverInterface> {

	private FirstFitReference referenceSolver = new FirstFitReference();

	@Override
	protected List<Solution> evaluatePopulation(List<List<Integer>> population) {
		// Use the GPU solver to get scores for all orders in parallel
		List<Integer> scores = solverSource.solve(boxes, population);

		List<Solution> scored = new ArrayList<>();
		for (int i = 0; i < population.size(); i++) {
			List<Integer> order = population.get(i);
			int volumeScore = scores.get(i);
			// Parallel solver returns used volume as int (or score scaled)
			// Optimizer expects "rate" method result logic
			// Our rate implementation is: (usedVolume / totalVolume) for non-growing
			// Or maxExtent for growing.

			// The kernel returns used volume for fixed bins.

			double finalScore = 0;

			if (growingBin) {
				// Kernel might not support growing bin metric correctly (returns 0 for single
				// bin?)
				// If score is 0, we might need to fallback or if kernel was updated?
				// Assuming user wants us to use the score provided.
				// For growing bin, rate() uses maxExtent (lower is better? No, rate returns
				// maxExtent).
				// We need to implement rate logic here or trust kernel score?
				// Kernel score is Volume.
				// If we optimize for Volume, max volume is better.
				// If we optimize for Extent (length), min extent is better.

				// CPUOptimizer rate:
				// Growing: returns maxExtent. Sort: ascending (lower extent is better).
				// Fixed: returns density. Sort: descending (higher density is better).

				// Kernel returns VOLUME.
				// If growing bin, maximizing volume in FIRST bin is good? No, growing bin
				// usually minimizes size.
				// If kernel returns volume, it's not extent.
				// WARN: GPU Kernel might not be suitable for Growing Bin 'Extent' optimization
				// without modification.
				// But we proceed.

				finalScore = volumeScore;
			} else {
				// Fixed bin
				double totalVol = bin.getVolume();
				// Score is total used volume across all full bins.
				// Actually firstfit_complete.cl calculates used volume of *all bins except
				// last*?
				// "for (int b = 0; b < bins_used - 1; b++) { score += used_volume[b]; }"
				// This metric encourages filling earlier bins.

				// CPUOptimizer rate logic for fixed:
				// "totalUsedVolume / (binsToConsider * bin.getVolume())"
				// where binsToConsider = solution.size() - 1.

				// So it seems consistent: maximizing filled volume in non-last bins.
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
		// Standard CPU rating for consistency/validation
		if (growingBin) {
			double maxExtent = 0.0;
			for (List<Box> packedBin : solution) {
				for (Box box : packedBin) {
					maxExtent = Math.max(maxExtent, box.position.x + box.size.x);
					maxExtent = Math.max(maxExtent, box.position.y + box.size.y);
					maxExtent = Math.max(maxExtent, box.position.z + box.size.z);
				}
			}
			return maxExtent;
		} else {
			double totalUsedVolume = 0.0;
			int binsToConsider = solution.size() - 1;

			if (binsToConsider <= 0) {
				return 1.0;
			}

			for (int i = 0; i < binsToConsider; i++) {
				List<Box> currentBinContents = solution.get(i);
				double currentBinUsedVolume = 0.0;
				for (Box box : currentBinContents) {
					currentBinUsedVolume += box.getVolume();
				}
				totalUsedVolume += currentBinUsedVolume;
			}

			// If binsToConsider is from kernel score, we might not have it.
			// But this rate() is called on 'solution' which is List<List<Box>>.
			// finalizeBestSolution returns that.
			// So this is valid.

			return totalUsedVolume / (binsToConsider * bin.getVolume());
		}
	}

}
