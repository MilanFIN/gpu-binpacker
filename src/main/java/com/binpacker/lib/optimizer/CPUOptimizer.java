package com.binpacker.lib.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import java.util.function.Supplier;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.solver.SolverInterface;

public class CPUOptimizer extends Optimizer<Supplier<SolverInterface>> {

	@Override
	protected List<Solution> evaluatePopulation(List<List<Integer>> population) {
		List<Solution> scored = new ArrayList<>();

		if (this.threaded) {
			int numThreads = Runtime.getRuntime().availableProcessors();
			ExecutorService executor = Executors.newFixedThreadPool(numThreads);
			List<Future<List<Solution>>> futures = new ArrayList<>();

			List<List<List<Integer>>> partitions = new ArrayList<>();
			int partitionSize = (int) Math.ceil((double) population.size() / numThreads);
			if (partitionSize == 0)
				partitionSize = 1;

			for (int i = 0; i < population.size(); i += partitionSize) {
				partitions.add(population.subList(i, Math.min(i + partitionSize, population.size())));
			}

			for (List<List<Integer>> chunk : partitions) {
				futures.add(executor.submit(() -> {
					SolverInterface localSolver = solverSource.get();
					List<Solution> chunkResults = new ArrayList<>();
					try {
						for (List<Integer> order : chunk) {
							List<Box> orderedBoxes = applyOrder(order);
							List<List<Box>> solved = localSolver.solve(orderedBoxes);
							double score = rate(solved, this.bin);
							chunkResults.add(new Solution(order, score, solved));
						}
					} finally {
						localSolver.release();
					}
					return chunkResults;
				}));
			}

			for (Future<List<Solution>> future : futures) {
				try {
					scored.addAll(future.get());
				} catch (InterruptedException | ExecutionException e) {
					Thread.currentThread().interrupt();
					System.err.println("Error processing task: " + e.getMessage());
				}
			}

			executor.shutdown();
			try {
				if (!executor.awaitTermination(3, TimeUnit.MINUTES)) {
					executor.shutdownNow();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				executor.shutdownNow();
			}
		} else {
			// Non-threaded
			SolverInterface localSolver = solverSource.get();
			try {
				for (List<Integer> order : population) {
					List<Box> orderedBoxes = applyOrder(order);
					List<List<Box>> solved = localSolver.solve(orderedBoxes);
					double score = rate(solved, this.bin);
					scored.add(new Solution(order, score, solved));
				}
			} finally {
				localSolver.release();
			}
		}

		return scored;
	}

	@Override
	protected List<List<Box>> finalizeBestSolution(Solution bestSolution) {
		return bestSolution.solved;
	}

	@Override
	public double rate(List<List<Box>> solution, Bin bin) {

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
			int binsToConsider = solution.size() - 1; // Exclude the last bin

			if (binsToConsider <= 0) {
				return 1.0; // No bins to consider or only one bin
			}

			for (int i = 0; i < binsToConsider; i++) {
				List<Box> currentBinContents = solution.get(i);
				double currentBinUsedVolume = 0.0;
				for (Box box : currentBinContents) {
					currentBinUsedVolume += box.getVolume();
				}
				totalUsedVolume += currentBinUsedVolume;
			}

			return totalUsedVolume / (binsToConsider * bin.getVolume());

		}

	}
}
