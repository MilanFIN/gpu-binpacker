package com.binpacker.lib.optimizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.solver.SolverInterface;

public abstract class Optimizer<S> {

	protected S solverSource;
	protected List<Box> boxes;
	protected Bin bin;

	protected List<List<Integer>> boxOrders; // Population
	protected Random random = new Random();
	protected int populationSize;
	private int eliteCount;
	protected boolean growingBin;
	protected String growAxis;

	protected boolean threaded;

	protected abstract List<Solution> evaluatePopulation(List<List<Integer>> population);

	protected abstract List<List<Box>> finalizeBestSolution(Solution bestSolution);

	public abstract double rate(List<List<Box>> solution, Bin bin);

	// ---- Initialize ----
	public void initialize(S solverSource, List<Box> boxes, Bin bin, boolean growingBin,
			String growAxis,
			int populationSize,
			int eliteCount, boolean threaded) {
		this.solverSource = solverSource;
		this.boxes = boxes;
		this.bin = bin;
		this.growingBin = growingBin;
		this.growAxis = growAxis;
		this.populationSize = populationSize;
		this.eliteCount = eliteCount;
		this.threaded = threaded;

		generateInitialPopulation();
	}

	public void generateInitialPopulation() {
		boxOrders = new ArrayList<>();

		List<Integer> base = new ArrayList<>();
		for (int i = 0; i < boxes.size(); i++)
			base.add(i);

		// // First order: growing by volume
		List<Integer> growingOrder = new ArrayList<>(base);
		Collections.sort(growingOrder,
				(i1, i2) -> Double.compare(boxes.get(i1).getVolume(),
						boxes.get(i2).getVolume()));
		boxOrders.add(growingOrder);

		// Second order: shrinking by volume
		List<Integer> shrinkingOrder = new ArrayList<>(base);
		Collections.sort(shrinkingOrder,
				(i1, i2) -> Double.compare(boxes.get(i2).getVolume(), boxes.get(i1).getVolume()));
		boxOrders.add(shrinkingOrder);

		// third order: shrinking by longest side
		List<Integer> shrinkingLongestOrder = new ArrayList<>(base);
		Collections.sort(shrinkingLongestOrder,
				(i1, i2) -> Double.compare(boxes.get(i2).getLongestSide(), boxes.get(i1).getLongestSide()));
		boxOrders.add(shrinkingLongestOrder);

		// Remaining orders: random
		for (int i = 2; i < populationSize; i++) {
			List<Integer> order = new ArrayList<>(base);
			Collections.shuffle(order, random);
			boxOrders.add(order);
		}

		this.populationSize = boxOrders.size();
	}

	// ---- Main GA Logic ----
	public List<List<Box>> executeNextGeneration() {

		// 1. Evaluate current population
		List<Solution> scored = evaluatePopulation(boxOrders);

		// 2. Sort best to worst
		if (!growingBin) {
			scored.sort(Comparator.comparingDouble(s -> -s.score));
		} else {
			scored.sort(Comparator.comparingDouble(s -> s.score));
		}

		// 3. Get best solution of this generation
		Solution bestOfGen = scored.get(0);
		List<List<Box>> bestSolutionPack = finalizeBestSolution(bestOfGen);

		// ---------------------------------------------------------
		// Build next generation
		// ---------------------------------------------------------
		List<List<Integer>> nextGen = new ArrayList<>();

		// Keep elite
		for (int i = 0; i < eliteCount && i < scored.size(); i++) {
			nextGen.add(new ArrayList<>(scored.get(i).order));
		}

		// Fill remaining
		while (nextGen.size() < populationSize) {
			if (random.nextBoolean()) {
				// crossover
				int idx1 = random.nextInt(Math.min(eliteCount, scored.size()));
				int idx2 = random.nextInt(Math.min(eliteCount, scored.size()));
				List<Integer> p1 = scored.get(idx1).order;
				List<Integer> p2 = scored.get(idx2).order;
				nextGen.add(crossOver(p1, p2));
			} else {
				// mutation
				int idx = random.nextInt(Math.min(eliteCount, scored.size()));
				List<Integer> p = scored.get(idx).order;
				nextGen.add(mutate(p));
			}
		}

		// Replace population
		this.boxOrders = nextGen;

		return bestSolutionPack;
	}

	protected List<Integer> crossOver(List<Integer> parent1, List<Integer> parent2) {
		int size = parent1.size();
		int cut1 = random.nextInt(size);
		int cut2 = random.nextInt(size);

		if (cut1 > cut2) {
			int t = cut1;
			cut1 = cut2;
			cut2 = t;
		}

		List<Integer> child = new ArrayList<>(Collections.nCopies(size, null));

		// 1. Copy the slice from parent2
		for (int i = cut1; i <= cut2; i++) {
			child.set(i, parent2.get(i));
		}

		// 2. Fill remaining positions from parent1 in order
		int fillPos = (cut2 + 1) % size;

		for (int i = 0; i < size; i++) {
			int gene = parent1.get((cut2 + 1 + i) % size);

			if (!child.contains(gene)) {
				child.set(fillPos, gene);
				fillPos = (fillPos + 1) % size;
			}
		}

		return child;
	}

	protected List<Integer> mutate(List<Integer> order) {
		List<Integer> mutatedOrder = new ArrayList<>(order);
		int index1 = random.nextInt(mutatedOrder.size());
		int index2 = random.nextInt(mutatedOrder.size());
		while (index1 == index2) {
			index2 = random.nextInt(mutatedOrder.size());
		}
		Collections.swap(mutatedOrder, index1, index2);
		return mutatedOrder;
	}

	public void release() {
		// Default no-op
	}

	// --- Helper: apply an index order to the box list ---
	protected List<Box> applyOrder(List<Integer> order) {
		List<Box> result = new ArrayList<>();
		for (Integer idx : order)
			result.add(boxes.get(idx));
		return result;
	}

}
