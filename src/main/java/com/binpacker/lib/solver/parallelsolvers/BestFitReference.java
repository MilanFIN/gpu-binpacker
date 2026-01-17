package com.binpacker.lib.solver.parallelsolvers;

import java.util.ArrayList;
import java.util.List;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.common.Space;

/**
 * Best-fit reference solver for reconstructing packing solutions.
 * Selects the space with minimum waste (tightest fit) for each box.
 */
public class BestFitReference implements ReferenceSolver {

	@Override
	public List<Bin> solve(List<Box> boxes, List<Integer> order, Bin binTemplate) {
		List<Bin> activeBins = new ArrayList<>();

		// Initialize first bin
		activeBins.add(new Bin(0, binTemplate.w, binTemplate.h, binTemplate.d, binTemplate.maxWeight));

		// Iterate through boxes in the given order
		for (int boxIndex : order) {
			Box originalBox = boxes.get(boxIndex);
			// Create a copy of the box to store placement
			Box box = new Box(originalBox.id, new Point3f(0, 0, 0),
					new Point3f(originalBox.size.x, originalBox.size.y, originalBox.size.z), originalBox.weight);

			boolean placed = false;

			// Best-fit with rotation: find the smallest fitting space across all bins and
			// all orientations
			int bestBin = -1;
			int bestSpace = -1;
			int bestOrientation = -1;
			double bestScore = Double.POSITIVE_INFINITY;

			// Generate all 6 orientations
			float[][] orientations = {
					{ box.size.x, box.size.y, box.size.z }, // original
					{ box.size.x, box.size.z, box.size.y }, // rotate around x
					{ box.size.y, box.size.x, box.size.z }, // rotate around z
					{ box.size.y, box.size.z, box.size.x }, // rotate around y
					{ box.size.z, box.size.x, box.size.y }, // diagonal 1
					{ box.size.z, box.size.y, box.size.x } // diagonal 2
			};

			// Try to fit in existing bins
			for (int b = 0; b < activeBins.size(); b++) {
				Bin bin = activeBins.get(b);

				// Skip if weight limit exceeded
				if (bin.maxWeight > 0 && bin.weight + box.weight > bin.maxWeight) {
					continue;
				}

				List<Space> spaces = bin.freeSpaces;

				// Iterate spaces in bin
				for (int s = 0; s < spaces.size(); s++) {
					Space sp = spaces.get(s);

					// Try all orientations
					for (int o = 0; o < orientations.length; o++) {
						float w = orientations[o][0];
						float h = orientations[o][1];
						float d = orientations[o][2];

						if (w <= sp.w && h <= sp.h && d <= sp.d) {
							// Score by position: prefer placements closer to origin (minimize x+y+z)
							double score = sp.x + sp.y + sp.z + b * 100000;

							// Update best fit if this orientation/space has lower score (closer to origin)
							if (score < bestScore) {
								bestScore = score;
								bestBin = b;
								bestSpace = s;
								bestOrientation = o;
								// break;
							}
						}
					}
				}
			}

			// Place box in best space with best orientation if found
			if (bestBin >= 0) {
				placed = true;
				Bin bin = activeBins.get(bestBin);
				List<Space> spaces = bin.freeSpaces;
				Space sp = spaces.get(bestSpace);

				// Use best orientation dimensions
				float boxW = orientations[bestOrientation][0];
				float boxH = orientations[bestOrientation][1];
				float boxD = orientations[bestOrientation][2];

				// Update box size to reflect chosen orientation
				box.size.x = boxW;
				box.size.y = boxH;
				box.size.z = boxD;

				// Set position
				box.position.x = sp.x;
				box.position.y = sp.y;
				box.position.z = sp.z;
				bin.boxes.add(box);
				bin.weight += box.weight;

				// Remove used space (swap with last for efficiency)
				int lastIdx = spaces.size() - 1;
				if (bestSpace != lastIdx) {
					spaces.set(bestSpace, spaces.get(lastIdx));
				}
				spaces.remove(lastIdx);

				// Create new spaces (Guillotine Split)
				// Right
				if (sp.w - boxW > 0) {
					spaces.add(new Space(
							sp.x + boxW, sp.y, sp.z,
							sp.w - boxW, sp.h, sp.d));
				}
				// Top
				if (sp.h - boxH > 0) {
					spaces.add(new Space(
							sp.x, sp.y + boxH, sp.z,
							boxW, sp.h - boxH, sp.d));
				}
				// Front
				if (sp.d - boxD > 0) {
					spaces.add(new Space(
							sp.x, sp.y, sp.z + boxD,
							boxW, boxH, sp.d - boxD));
				}
			}

			// If not placed, create new bin
			if (!placed) {
				Bin newBin = new Bin(activeBins.size(), binTemplate.w, binTemplate.h, binTemplate.d,
						binTemplate.maxWeight);
				activeBins.add(newBin);

				Space sp = newBin.freeSpaces.get(0); // The single initial space

				// Try all orientations in new bin
				int newBinOrientation = 0;
				for (int o = 0; o < orientations.length; o++) {
					float w = orientations[o][0];
					float h = orientations[o][1];
					float d = orientations[o][2];

					if (w <= sp.w && h <= sp.h && d <= sp.d) {
						newBinOrientation = o;
						// break; // Use first fitting orientation
					}
				}

				// Use chosen orientation
				float boxW = orientations[newBinOrientation][0];
				float boxH = orientations[newBinOrientation][1];
				float boxD = orientations[newBinOrientation][2];

				// Update box size
				box.size.x = boxW;
				box.size.y = boxH;
				box.size.z = boxD;

				if (boxW <= sp.w && boxH <= sp.h && boxD <= sp.d) {
					box.position.x = sp.x;
					box.position.y = sp.y;
					box.position.z = sp.z;
					newBin.boxes.add(box);
					newBin.weight += box.weight;

					newBin.freeSpaces.remove(0);

					// Right
					if (sp.w - boxW > 0) {
						newBin.freeSpaces.add(new Space(
								sp.x + boxW, sp.y, sp.z,
								sp.w - boxW, sp.h, sp.d));
					}
					// Top
					if (sp.h - boxH > 0) {
						newBin.freeSpaces.add(new Space(
								sp.x, sp.y + boxH, sp.z,
								boxW, sp.h - boxH, sp.d));
					}
					// Front
					if (sp.d - boxD > 0) {
						newBin.freeSpaces.add(new Space(
								sp.x, sp.y, sp.z + boxD,
								boxW, boxH, sp.d - boxD));
					}
				} else {
					// Should not happen if box fits in bin template
					System.err.println("Box " + box.id + " too large for bin template in any orientation!");
				}
			}
		}

		return activeBins;
	}

}
