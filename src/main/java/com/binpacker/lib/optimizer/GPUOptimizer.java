package com.binpacker.lib.optimizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.solver.parallelsolvers.opencl.OpenCLSolver;
import com.binpacker.lib.solver.parallelsolvers.ParallelSolverInterface;
import com.binpacker.lib.solver.parallelsolvers.ReferenceSolver;

public class GPUOptimizer extends Optimizer<ParallelSolverInterface> {

	private ReferenceSolver referenceSolver;

	@Override
	protected List<Solution> evaluatePopulation(List<List<Integer>> population) {
		// Get reference solver from OpenCLSolver if available
		if (referenceSolver == null && solverSource instanceof OpenCLSolver) {
			referenceSolver = ((OpenCLSolver) solverSource).getReferenceSolver();
		}

		// Configure and compile kernel if needed (Template support)
		if (solverSource instanceof OpenCLSolver) {
			OpenCLSolver gpuSolver = (OpenCLSolver) solverSource;

			if (gpuSolver.isTemplate() && !gpuSolver.isCompiled()) {
				// Estimate needed resources using ReferenceSolver
				if (referenceSolver != null) {
					// Create a dummy identity order
					List<Integer> identityOrder = new ArrayList<>();
					for (int i = 0; i < boxes.size(); i++) {
						identityOrder.add(i);
					}

					// Deep copy boxes to avoid side effects
					List<Box> testBoxes = new ArrayList<>();
					for (Box b : boxes) {
						testBoxes.add(new Box(b.id, new com.binpacker.lib.common.Point3f(0, 0, 0),
								new com.binpacker.lib.common.Point3f(b.size.x, b.size.y, b.size.z)));
					}

					List<Bin> solved = referenceSolver.solve(testBoxes, identityOrder,
							new com.binpacker.lib.solver.common.SolverProperties(bin, growingBin, growAxis,
									rotationAxes));

					int maxBins = solved.size() * 2;
					// Ensure at least some bins
					if (maxBins < 64)
						maxBins = 64;

					int maxSpaces = 0;
					for (Bin b : solved) {
						if (b.freeSpaces.size() > maxSpaces) {
							maxSpaces = b.freeSpaces.size();
						}
					}
					// Double it for safety, ensure minimum
					maxSpaces = (maxSpaces == 0 ? 512 : maxSpaces * 2);
					if (maxSpaces < 512)
						maxSpaces = 512;

					System.out.println(
							"Configuring Kernel with MAX_BINS=" + maxBins + ", MAX_SPACES_PER_BIN=" + maxSpaces);
					gpuSolver.compileKernel(maxBins, maxSpaces);
				} else {
					// Fallback defaults if no reference solver
					gpuSolver.compileKernel(64, 512);
				}
			}
		}

		// Use the GPU solver to get scores for all orders in parallel
		List<Double> scores = solverSource.solve(boxes, population);

		List<Solution> scored = new ArrayList<>();
		for (int i = 0; i < population.size(); i++) {
			List<Integer> order = population.get(i);
			double volumeScore = scores.get(i);

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
		List<Bin> packedBins = referenceSolver.solve(boxes, bestSolution.order,
				new com.binpacker.lib.solver.common.SolverProperties(bin, growingBin, growAxis, rotationAxes));

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
