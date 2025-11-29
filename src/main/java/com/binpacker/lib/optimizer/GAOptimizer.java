package com.binpacker.lib.optimizer;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import com.binpacker.lib.common.Box;

public class GAOptimizer extends Optimizer {

	private Random random = new Random();

	@Override
	public void generateInitialPopulation() {
		this.boxOrders = new ArrayList<>();
		List<Integer> base = new ArrayList<>();
		for (int i = 0; i < boxes.size(); i++) {
			base.add(i);
		}

		// First order: growing by volume
		List<Integer> growingOrder = new ArrayList<>(base);
		Collections.sort(growingOrder,
				(i1, i2) -> Double.compare(boxes.get(i1).getVolume(), boxes.get(i2).getVolume()));
		boxOrders.add(growingOrder);

		// Second order: shrinking by volume
		List<Integer> shrinkingOrder = new ArrayList<>(base);
		Collections.sort(shrinkingOrder,
				(i1, i2) -> Double.compare(boxes.get(i2).getVolume(), boxes.get(i1).getVolume()));
		boxOrders.add(shrinkingOrder);

		// Remaining orders: random
		for (int i = 2; i < populationSize; i++) {
			List<Integer> order = new ArrayList<>(base);
			Collections.shuffle(order, random);
			boxOrders.add(order);
		}

	}

	@Override
	public double rate(List<List<Box>> solution, Box bin) {

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

	@Override
	protected List<Integer> crossOver(List<Integer> parent1, List<Integer> parent2) {
		int size = parent1.size();
		Random rand = new Random();

		int cut1 = rand.nextInt(size);
		int cut2 = rand.nextInt(size);

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

	@Override
	protected List<Integer> mutate(List<Integer> order) {

		List<Integer> mutatedOrder = new ArrayList<>(order);
		int index1 = random.nextInt(mutatedOrder.size());
		int index2 = random.nextInt(mutatedOrder.size());
		// Ensure index1 and index2 are different
		while (index1 == index2) {
			index2 = random.nextInt(mutatedOrder.size());
		}
		// Swap elements
		Collections.swap(mutatedOrder, index1, index2);

		return mutatedOrder;

	}
}
