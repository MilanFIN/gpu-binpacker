package com.binpacker.lib.solver.parallelsolvers.solvers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.solver.parallelsolvers.BestFitEMSReference;

class BestFitEMSReferenceTest {

	@Test
	void testBasicPacking() {
		BestFitEMSReference solver = new BestFitEMSReference();

		List<Box> boxes = new ArrayList<>();
		// Box 1: 2x2x2
		boxes.add(new Box(1, new Point3f(0, 0, 0), new Point3f(2, 2, 2)));
		// Box 2: 3x3x3
		boxes.add(new Box(2, new Point3f(0, 0, 0), new Point3f(3, 3, 3)));

		// Order: 0, 1
		List<Integer> order = Arrays.asList(0, 1);

		Bin binTemplate = new Bin(0, 10, 10, 10);

		List<Bin> resultBins = solver.solve(boxes, order, binTemplate);

		assertEquals(1, resultBins.size(), "Should fit in one bin");
		Bin bin = resultBins.get(0);
		assertEquals(2, bin.boxes.size(), "Should have 2 boxes");

		// Verify Box 1 (First placed, typically at 0,0,0)
		Box b1 = bin.boxes.get(0);
		assertEquals(1, b1.id);
		// Note: Reference logic places at 0,0,0 for first box in empty bin
		assertEquals(0, b1.position.x);
		assertEquals(0, b1.position.y);
		assertEquals(0, b1.position.z);

		// Verify Box 2
		Box b2 = bin.boxes.get(1);
		assertEquals(2, b2.id);
		// Box 2 should not overlap with Box 1.
		// 2x2x2 box at 0,0,0.
		// Spaces created (EMS):
		// Right: x=2, w=8
		// Top: y=2, h=8
		// Front: z=2, d=8
		// Box 2 (3x3x3) should fit in Right (8x10x10), Top (10x8x10), or Front
		// (10x10x8).
		// Score logic: minimize x+y+z.
		// Right: at 2,0,0 -> score 2+0+0 = 2.
		// Top: at 0,2,0 -> score 0+2+0 = 2.
		// Front: at 0,0,2 -> score 0+0+2 = 2.
		// Tie-breaking depends on iteration order of spaces.
		// Order of addition in code: Right, Top, Front.
		// So they are in list as [Right, Top, Front].
		// Iteration s=0 (Right). Score 2. Best.
		// Iteration s=1 (Top). Score 2. Not < Best (2).
		// Iteration s=2 (Front). Score 2. Not < Best (2).
		// So likely places in Right space at 2,0,0.

		assertFalse(checkOverlap(b1, b2), "Boxes should not overlap");
	}

	private boolean checkOverlap(Box b1, Box b2) {
		return (b1.position.x < b2.position.x + b2.size.x &&
				b1.position.x + b1.size.x > b2.position.x &&
				b1.position.y < b2.position.y + b2.size.y &&
				b1.position.y + b1.size.y > b2.position.y &&
				b1.position.z < b2.position.z + b2.size.z &&
				b1.position.z + b1.size.z > b2.position.z);
	}
}
