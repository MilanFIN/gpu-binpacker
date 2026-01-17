package com.binpacker.lib.solver.parallelsolvers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Point3f;

public class ReferenceSolverWeightTest {

	@Test
	public void testFirstFitReferenceWeightLimit() {
		// Bin: 100x100x100, Max Weight: 10
		Bin binTemplate = new Bin(0, 100, 100, 100, 10);

		List<Box> boxes = new ArrayList<>();
		// Box 1: 10x10x10, Weight 6
		Box b1 = new Box(0, new Point3f(0, 0, 0), new Point3f(10, 10, 10));
		b1.weight = 6;
		boxes.add(b1);

		// Box 2: 10x10x10, Weight 5
		Box b2 = new Box(1, new Point3f(0, 0, 0), new Point3f(10, 10, 10));
		b2.weight = 5;
		boxes.add(b2);

		// Order 0, 1
		List<Integer> order = new ArrayList<>();
		order.add(0);
		order.add(1);

		FirstFitReference solver = new FirstFitReference();
		List<Bin> result = solver.solve(boxes, order, binTemplate);

		// Expect 2 bins because 6 + 5 = 11 > 10
		assertEquals(2, result.size(), "Should use 2 bins due to weight limit");
		assertEquals(6.0f, result.get(0).weight, 0.001, "Bin 1 weight should be 6");
		assertEquals(5.0f, result.get(1).weight, 0.001, "Bin 2 weight should be 5");
	}

	@Test
	public void testBestFitReferenceWeightLimit() {
		// Bin: 100x100x100, Max Weight: 10
		Bin binTemplate = new Bin(0, 100, 100, 100, 10);

		List<Box> boxes = new ArrayList<>();
		// Box 1: 10x10x10, Weight 6
		Box b1 = new Box(0, new Point3f(0, 0, 0), new Point3f(10, 10, 10));
		b1.weight = 6;
		boxes.add(b1);

		// Box 2: 10x10x10, Weight 5
		Box b2 = new Box(1, new Point3f(0, 0, 0), new Point3f(10, 10, 10));
		b2.weight = 5;
		boxes.add(b2);

		// Order 0, 1
		List<Integer> order = new ArrayList<>();
		order.add(0);
		order.add(1);

		BestFitReference solver = new BestFitReference();
		List<Bin> result = solver.solve(boxes, order, binTemplate);

		// Expect 2 bins because 6 + 5 = 11 > 10
		assertEquals(2, result.size(), "Should use 2 bins due to weight limit");
		assertEquals(6.0f, result.get(0).weight, 0.001, "Bin 1 weight should be 6");
		assertEquals(5.0f, result.get(1).weight, 0.001, "Bin 2 weight should be 5");
	}

	@Test
	public void testBestFitEMSReferenceWeightLimit() {
		// Bin: 100x100x100, Max Weight: 10
		Bin binTemplate = new Bin(0, 100, 100, 100, 10);

		List<Box> boxes = new ArrayList<>();
		// Box 1: 10x10x10, Weight 6
		Box b1 = new Box(0, new Point3f(0, 0, 0), new Point3f(10, 10, 10));
		b1.weight = 6;
		boxes.add(b1);

		// Box 2: 10x10x10, Weight 5
		Box b2 = new Box(1, new Point3f(0, 0, 0), new Point3f(10, 10, 10));
		b2.weight = 5;
		boxes.add(b2);

		// Order 0, 1
		List<Integer> order = new ArrayList<>();
		order.add(0);
		order.add(1);

		BestFitEMSReference solver = new BestFitEMSReference();
		List<Bin> result = solver.solve(boxes, order, binTemplate);

		// Expect 2 bins because 6 + 5 = 11 > 10
		assertEquals(2, result.size(), "Should use 2 bins due to weight limit");
		assertEquals(6.0f, result.get(0).weight, 0.001, "Bin 1 weight should be 6");
		assertEquals(5.0f, result.get(1).weight, 0.001, "Bin 2 weight should be 5");
	}
}
