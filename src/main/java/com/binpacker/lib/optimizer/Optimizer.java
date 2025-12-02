package com.binpacker.lib.optimizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.solver.Solver;

public abstract class Optimizer {

	private Solver solver;
	protected List<Box> boxes;
	private Bin bin;

	protected List<List<Integer>> boxOrders; // Population
	private Random random = new Random();
	protected int populationSize;
	private int eliteCount;
	protected boolean growingBin;
	protected String growAxis;

	protected abstract List<Integer> crossOver(List<Integer> parent1, List<Integer> parent2);

	protected abstract List<Integer> mutate(List<Integer> order);

	public abstract double rate(List<List<Box>> solution, Bin bin);

	// ---- Initialize ----
	public void initialize(Solver solver, List<Box> boxes, Bin bin, boolean growingBin, String growAxis,
			int populationSize,
			int eliteCount) {
		this.solver = solver;
		this.boxes = boxes;
		this.bin = bin;
		this.growingBin = growingBin;
		this.growAxis = growAxis;
		this.populationSize = populationSize;
		this.eliteCount = eliteCount;

		generateInitialPopulation();
	}

	public void generateInitialPopulation() {
		boxOrders = new ArrayList<>();

		List<Integer> base = new ArrayList<>();
		for (int i = 0; i < boxes.size(); i++)
			base.add(i);

		for (int i = 0; i < populationSize; i++) {
			List<Integer> order = new ArrayList<>(base);
			Collections.shuffle(order, random);
			boxOrders.add(order);
		}
	}

	// ---- Main GA Logic ----
	public List<List<Box>> executeNextGeneration() {

		List<ScoredSolution> scored = new ArrayList<>();

		int numThreads = Runtime.getRuntime().availableProcessors();
		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		List<Future<ScoredSolution>> futures = new ArrayList<>();

		for (List<Integer> order : boxOrders) {
			futures.add(executor.submit(() -> {
				List<Box> orderedBoxes = applyOrder(order);
				List<List<Box>> solved = solver.solve(orderedBoxes, bin, growingBin, growAxis);
				double score = rate(solved, this.bin);
				return new ScoredSolution(order, score, solved);
			}));
		}

		for (Future<ScoredSolution> future : futures) {
			try {
				scored.add(future.get());
			} catch (InterruptedException | ExecutionException e) {
				Thread.currentThread().interrupt(); // Restore interrupt status
				// Handle or log the exception, e.g., System.err.println("Error processing task:
				// " + e.getMessage());
			}
		}
		executor.shutdown();
		try {
			if (!executor.awaitTermination(3, TimeUnit.MINUTES)) { // Wait for tasks to complete
				// Optionally, force shutdown if tasks don't complete in time
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			executor.shutdownNow(); // Cancel currently executing tasks
		}

		// Sort best to worst, order is reverse when packing to a single bin
		// (lower height is better)
		if (!growingBin) {
			scored.sort(Comparator.comparingDouble(s -> -s.score));
		} else {
			scored.sort(Comparator.comparingDouble(s -> s.score));
		}

		// Best solution of this generation → returned
		List<List<Box>> bestSolution = scored.get(0).solved;

		// ---------------------------------------------------------
		// Build next generation
		// ---------------------------------------------------------
		List<List<Integer>> nextGen = new ArrayList<>();

		// 1. Keep the elite (top 20%)
		for (int i = 0; i < eliteCount; i++) {
			nextGen.add(new ArrayList<>(scored.get(i).order));
		}

		// 2. Fill remaining 80% with crossover or mutation
		while (nextGen.size() < populationSize) {

			if (random.nextBoolean()) {
				// crossover
				List<Integer> p1 = scored.get(random.nextInt(eliteCount)).order;
				List<Integer> p2 = scored.get(random.nextInt(eliteCount)).order;
				nextGen.add(crossOver(p1, p2));
			} else {
				// mutation
				List<Integer> p = scored.get(random.nextInt(eliteCount)).order;
				nextGen.add(mutate(p));
			}
		}

		// Replace population and increment generation counter
		this.boxOrders = nextGen;

		return bestSolution;
	}

	// --- Helper: apply an index order to the box list ---
	private List<Box> applyOrder(List<Integer> order) {
		List<Box> result = new ArrayList<>();
		for (Integer idx : order)
			result.add(boxes.get(idx));
		return result;
	}

	private static class ScoredSolution {
		final List<Integer> order;
		final double score;
		final List<List<Box>> solved;

		ScoredSolution(List<Integer> order, double score, List<List<Box>> solved) {
			this.order = order;
			this.score = score;
			this.solved = solved;
		}
	}
}
