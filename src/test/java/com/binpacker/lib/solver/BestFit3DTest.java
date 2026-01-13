package com.binpacker.lib.solver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.solver.common.SolverProperties;
import com.binpacker.lib.solver.cpusolvers.BestFit3D;

class BestFit3DTest {

	@Test
	void testSolve() {
		BestFit3D solver = new BestFit3D();
		List<Box> boxes = new ArrayList<>();
		boxes.add(new Box(1, new Point3f(0, 0, 0), new Point3f(2, 2, 2)));
		boxes.add(new Box(2, new Point3f(0, 0, 0), new Point3f(3, 3, 3)));
		Bin binTemplate = new Bin(0, 10, 10, 10);

		SolverProperties properties = new SolverProperties(binTemplate, false, "x", List.of(0, 1, 2));
		solver.init(properties);
		List<List<Box>> result = solver.solve(boxes);

		// check that both boxes ended in the bin in the same order as in the
		// original queue
		assertEquals(1, result.size());
		assertEquals(2, result.get(0).size());

		assertEquals(boxes.get(0).id, result.get(0).get(0).id);
		assertEquals(boxes.get(1).id, result.get(0).get(1).id);

	}
}
