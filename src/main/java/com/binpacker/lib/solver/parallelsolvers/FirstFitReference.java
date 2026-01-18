package com.binpacker.lib.solver.parallelsolvers;

import java.util.ArrayList;
import java.util.List;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.common.Space;

public class FirstFitReference implements ReferenceSolver {

	@Override

	public List<Bin> solve(List<Box> boxes, List<Integer> order,
			com.binpacker.lib.solver.common.SolverProperties properties) {
		List<Bin> activeBins = new ArrayList<>();
		Bin binTemplate = properties.bin;
		List<Integer> allowedRotations = properties.rotationAxes;

		// Initialize first bin
		activeBins.add(new Bin(0, binTemplate.w, binTemplate.h, binTemplate.d, binTemplate.maxWeight));

		// Iterate through boxes in the given order
		for (int boxIndex = 0; boxIndex < boxes.size(); boxIndex++) {
			Box originalBox = boxes.get(order.get(boxIndex));
			// Create a copy of the box to store placement
			Box box = new Box(originalBox.id, new Point3f(0, 0, 0),
					new Point3f(originalBox.size.x, originalBox.size.y, originalBox.size.z), originalBox.weight);

			boolean placed = false;

			// Generate valid orientations
			float[][] orientations = {
					{ box.size.x, box.size.y, box.size.z }, // 0: original
					{ box.size.x, box.size.z, box.size.y }, // 1: rotate around x
					{ box.size.y, box.size.x, box.size.z }, // 2: rotate around z
					{ box.size.z, box.size.y, box.size.x } // 3: diagonal 2
			};

			// Try to fit in existing bins (first-fit)
			for (Bin bin : activeBins) {
				// Skip if weight limit exceeded
				if (bin.maxWeight > 0 && bin.weight + box.weight > bin.maxWeight) {
					continue;
				}
				List<Space> spaces = bin.freeSpaces;

				// Iterate spaces in bin
				for (int s = 0; s < spaces.size(); s++) {
					Space sp = spaces.get(s);

					// Try all orientations, use first fitting
					for (int o = 0; o < 4; o++) {
						// Filter rotations
						if (o == 1 && (allowedRotations == null || !allowedRotations.contains(0)))
							continue;
						if (o == 2 && (allowedRotations == null || !allowedRotations.contains(1)))
							continue;
						if (o == 3 && (allowedRotations == null || !allowedRotations.contains(2)))
							continue;

						float w = orientations[o][0];
						float h = orientations[o][1];
						float d = orientations[o][2];

						if (w <= sp.w && h <= sp.h && d <= sp.d) {
							// System.out.println("Placed box in bin: " + bin.index);
							// Fit found!
							placed = true;

							// Update box size to reflect chosen orientation
							box.size.x = w;
							box.size.y = h;
							box.size.z = d;

							// Set position
							box.position.x = sp.x;
							box.position.y = sp.y;
							box.position.z = sp.z;
							bin.boxes.add(box);
							bin.weight += box.weight;

							// Remove used space (swap with last for efficiency, same as kernel)
							int lastIdx = spaces.size() - 1;
							if (s != lastIdx) {
								spaces.set(s, spaces.get(lastIdx));
							}
							spaces.remove(lastIdx);

							// Create new spaces (Guillotine Split)
							// Right
							if (sp.w - w > 0f) {
								spaces.add(new Space(
										sp.x + w, sp.y, sp.z,
										sp.w - w, sp.h, sp.d));
							}
							// Top
							if (sp.h - h > 0f) {
								spaces.add(new Space(
										sp.x, sp.y + h, sp.z,
										w, sp.h - h, sp.d));
							}
							// Front
							if (sp.d - d > 0f) {
								spaces.add(new Space(
										sp.x, sp.y, sp.z + d,
										w, h, sp.d - d));
							}

							break; // Break orientation loop - found a fit
						}
					}

					if (placed)
						break; // Break space loop
				}

				if (placed)
					break; // Break bin loop
			}

			// If not placed, create new bin
			if (!placed) {
				// System.out.println("new bin at box: " + boxIndex);
				Bin newBin = new Bin(activeBins.size(), binTemplate.w, binTemplate.h, binTemplate.d,
						binTemplate.maxWeight);
				activeBins.add(newBin);

				// boxIndex--;

				Space sp = newBin.freeSpaces.get(0); // The single initial space

				// Try to fit in new bin with allowed rotations
				boolean placedInNewBin = false;
				for (int o = 0; o < 4; o++) {
					// Filter rotations
					if (o == 1 && (allowedRotations == null || !allowedRotations.contains(0)))
						continue;
					if (o == 2 && (allowedRotations == null || !allowedRotations.contains(1)))
						continue;
					if (o == 3 && (allowedRotations == null || !allowedRotations.contains(2)))
						continue;

					float w = orientations[o][0];
					float h = orientations[o][1];
					float d = orientations[o][2];

					if (w <= sp.w && h <= sp.h && d <= sp.d) {
						box.size.x = w;
						box.size.y = h;
						box.size.z = d;
						placedInNewBin = true;
						break; // Found first fit in new bin
					}
				}

				if (placedInNewBin) {
					box.position.x = sp.x;
					box.position.y = sp.y;
					box.position.z = sp.z;
					newBin.boxes.add(box);
					newBin.weight += box.weight;

					newBin.freeSpaces.remove(0);

					// Right
					if (newBin.w - box.size.x > 0.0f) {
						newBin.freeSpaces.add(new Space(
								box.size.x, 0, 0,
								sp.w - box.size.x, sp.h, sp.d));
					}
					// Top
					if (newBin.h - box.size.y > 0.0f) {
						newBin.freeSpaces.add(new Space(
								0, box.size.y, 0,
								box.size.x, sp.h - box.size.y, sp.d));
					}
					// Front
					if (newBin.d - box.size.z > 0.0f) {
						newBin.freeSpaces.add(new Space(
								0, 0, box.size.z,
								box.size.x, box.size.y, sp.d - box.size.z));
					}

				} else {
					// Should not happen if box fits in bin template
					System.err.println("Box " + box.id + " too large for bin template!");
				}

			}
		}

		return activeBins;
	}

}
