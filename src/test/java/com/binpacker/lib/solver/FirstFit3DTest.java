package com.binpacker.lib.solver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.solver.common.SolverProperties;
import com.binpacker.lib.solver.cpusolvers.FirstFit3D;

class FirstFit3DTest {

	@Test
	void testSolve() {
		FirstFit3D solver = new FirstFit3D();
		List<Box> boxes = new ArrayList<>();
		boxes.add(new Box(1, new Point3f(0, 0, 0), new Point3f(2, 2, 2)));
		boxes.add(new Box(2, new Point3f(0, 0, 0), new Point3f(3, 3, 3)));
		Bin binTemplate = new Bin(0, 10, 10, 10);

		SolverProperties properties = new SolverProperties(binTemplate, false, "x", List.of(0, 1, 2));
		solver.init(properties);
		List<List<Box>> result = solver.solve(boxes);

		// both boxes were placed in the bin
		assertEquals(1, result.size());
		assertEquals(2, result.get(0).size());

		// boxes are in the bin in the same order as they were in the original list
		assertEquals(result.get(0).get(0).id, boxes.get(0).id);
		assertEquals(result.get(0).get(1).id, boxes.get(1).id);
	}
}
