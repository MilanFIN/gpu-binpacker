package com.binpacker.lib.solver.parallelsolvers;

import java.util.ArrayList;
import java.util.List;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.common.Space;

/**
 * Best-fit EMS reference solver for reconstructing packing solutions.
 * Directly translates the logic from bestfit_ems.cl to Java.
 */
public class BestFitEMSReference implements ReferenceSolver {

	@Override
	public List<Bin> solve(List<Box> boxes, List<Integer> order,
			com.binpacker.lib.solver.common.SolverProperties properties) {
		List<Bin> activeBins = new ArrayList<>();
		Bin binTemplate = properties.bin;
		List<Integer> allowedRotations = properties.rotationAxes;

		// Initialize first bin
		activeBins.add(new Bin(0, binTemplate.w, binTemplate.h, binTemplate.d, binTemplate.maxWeight));

		// Iterate through boxes in the given order
		for (int boxIndex : order) {
			Box originalBox = boxes.get(boxIndex);

			// Create a local working copy of the box
			// Note: The kernel uses struct Box { w, h, d }. Code below uses lib.common.Box.
			// We'll update the position of this box copy when placed.
			Box box = new Box(originalBox.id, new Point3f(0, 0, 0),
					new Point3f(originalBox.size.x, originalBox.size.y, originalBox.size.z), originalBox.weight);

			boolean placed = false;

			// Best-fit parameters
			int bestBinIndex = -1;
			int bestSpaceIndex = -1;
			int bestOrientation = -1;
			double bestScore = Double.POSITIVE_INFINITY;

			// Define valid orientations
			// {w, h, d}
			float[][] orientations = {
					{ box.size.x, box.size.y, box.size.z }, // 0: original
					{ box.size.x, box.size.z, box.size.y }, // 1: rotate around x
					{ box.size.y, box.size.x, box.size.z }, // 2: rotate around z
					{ box.size.z, box.size.y, box.size.x } // 3: diagonal 2
			};

			// 1. Find Best Fit
			// Kernel: for (int b = 0; b < bins_used; b++)
			// Matches Java: Iterate all current bins
			for (int b = 0; b < activeBins.size(); b++) {
				Bin bin = activeBins.get(b);

				// Skip bin if weight limit would be exceeded
				if (bin.maxWeight > 0 && bin.weight + box.weight > bin.maxWeight) {
					continue;
				}

				List<Space> spaces = bin.freeSpaces;

				for (int s = 0; s < spaces.size(); s++) {
					Space sp = spaces.get(s);

					// Try all orientations
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
							// Score: distance from origin + bin penalty
							double score = sp.x + sp.y + sp.z + b * 100000.0;

							if (score < bestScore) {
								bestScore = score;
								bestBinIndex = b;
								bestSpaceIndex = s;
								bestOrientation = o;
							}
						}
					}
				}

				// Compatibility with kernel "break logic":
				// In kernel, we iterate ALL bins to find GLOBAL best.
				// However, the comment in kernel says:
				// "Optimizing: if we found a fit in this bin... break"
				// But the kernel code posted currently DOES NOT break. It continues to search
				// all bins.
				// Line 149 in kernel: if (best_bin != -1 && best_bin == b) break;
				// Wait, checking the kernel Step 19 line 149:
				// if (best_bin != -1 && best_bin == b) { break; }
				// This means if we found ANY fit in bin 'b', we stop searching bin 'b+1',
				// 'b+2'...
				// So it finds the best fit in the *first bin that fits*.
				if (bestBinIndex != -1 && bestBinIndex == b) {
					break;
				}
			}

			// 2. Place Box
			if (bestBinIndex >= 0) {
				placed = true;
				Bin bin = activeBins.get(bestBinIndex);
				List<Space> spaces = bin.freeSpaces;
				Space sp = spaces.get(bestSpaceIndex); // The space we are placing into (copy reference)

				// Get dimensions
				float boxW = orientations[bestOrientation][0];
				float boxH = orientations[bestOrientation][1];
				float boxD = orientations[bestOrientation][2];

				float boxX = sp.x;
				float boxY = sp.y;
				float boxZ = sp.z;

				// Update box props and add to bin
				box.size.x = boxW;
				box.size.y = boxH;
				box.size.z = boxD;
				box.position.x = boxX;
				box.position.y = boxY;
				box.position.z = boxZ;
				bin.boxes.add(box);
				bin.weight += box.weight;

				// Remove the used space
				// Kernel: spaces[base + s] = spaces[base + space_count[b]]; decrease count;
				int lastIdx = spaces.size() - 1;
				if (bestSpaceIndex != lastIdx) {
					spaces.set(bestSpaceIndex, spaces.get(lastIdx));
				}
				spaces.remove(lastIdx);

				// A. Add splits from the placed space (BSP-style)
				// Kernel logic:
				// Right
				if (sp.w - boxW > 0.0f) {
					spaces.add(new Space(sp.x + boxW, sp.y, sp.z, sp.w - boxW, sp.h, sp.d));
				}
				// Top
				if (sp.h - boxH > 0.0f) {
					// Note: Kernel says sp.w, not boxW (Maximal space)
					spaces.add(new Space(sp.x, sp.y + boxH, sp.z, sp.w, sp.h - boxH, sp.d));
				}
				// Front
				if (sp.d - boxD > 0.0f) {
					// Note: Kernel says sp.w, sp.h
					spaces.add(new Space(sp.x, sp.y, sp.z + boxD, sp.w, sp.h, sp.d - boxD));
				}

				// B. Prune intersecting spaces (EMS)
				// Loop backwards
				for (int k = spaces.size() - 1; k >= 0; k--) {
					Space other = spaces.get(k);

					if (checkCollision(boxX, boxY, boxZ, boxW, boxH, boxD, other)) {
						// Remove other space
						int lastK = spaces.size() - 1;
						if (k != lastK) {
							spaces.set(k, spaces.get(lastK));
						}
						spaces.remove(lastK);

						// Split other space into up to 6 new spaces
						// 1. Right
						if (boxX + boxW < other.x + other.w) {
							spaces.add(new Space(
									boxX + boxW, other.y, other.z,
									(other.x + other.w) - (boxX + boxW), other.h, other.d));
						}
						// 2. Left
						if (boxX > other.x) {
							spaces.add(new Space(
									other.x, other.y, other.z,
									boxX - other.x, other.h, other.d));
						}
						// 3. Top
						if (boxY + boxH < other.y + other.h) {
							spaces.add(new Space(
									other.x, boxY + boxH, other.z,
									other.w, (other.y + other.h) - (boxY + boxH), other.d));
						}
						// 4. Bottom
						if (boxY > other.y) {
							spaces.add(new Space(
									other.x, other.y, other.z,
									other.w, boxY - other.y, other.d));
						}
						// 5. Front
						if (boxZ + boxD < other.z + other.d) {
							spaces.add(new Space(
									other.x, other.y, boxZ + boxD,
									other.w, other.h, (other.z + other.d) - (boxZ + boxD)));
						}
						// 6. Back
						if (boxZ > other.z) {
							spaces.add(new Space(
									other.x, other.y, other.z,
									other.w, other.h, boxZ - other.z));
						}

						// Because we removed an element (swapped with last), the element at 'k'
						// is now a different element (the one that was last).
						// However, we just processed 'k'. The loop continues to k-1.
						// Wait, if we swap with last, the element at 'k' is NEW and has NOT been
						// checked.
						// Java standard for loop "k--" will move to k-1.
						// We must process the *new* element at k if we swapped?
						// The kernel:
						// for (int k = space_count[b] - 1; k >= 0; k--) {
						// if (k >= space_count[b]) continue; // Safety
						// ...
						// if collision:
						// space_count[b]--;
						// spaces[k] = spaces[space_count[b]];
						// add spaces to end...
						// }
						// In the kernel, if we swap, the element at `k` becomes the one that was at
						// end.
						// The loop decrements `k`, so it *skips* checking the element we just swapped
						// in?
						// Let's verify behavior.
						// If I have [A, B, C] and k=1 (B).
						// Collision B. Swap B with C. List: [A, C]. Count=2.
						// Loop decrements k -> 0. Checks A.
						// element C is never checked against the box?
						// Yes, in the kernel loop `k--`, it moves left.
						// The new spaces are added to the *end* (count++).
						// Since k moves down, it won't check the newly added spaces (good, infinite
						// loop avoidance).
						// But does it check the one swapped from the end?
						// If index `k` is swapped with `end`, and we move to `k-1`, we MISS checking
						// the element that was at `end`.
						// Is this a bug in the kernel or intended?
						// Usually backward iteration with remove/swap is:
						// remove(k); k--; (if simple array list shift)
						// But swap-remove:
						// elem[k] = elem[last]; last--;
						// We must re-check `k` because it contains a new unchecked value!
						// The kernel code:
						// spaces[base + k] = spaces[base + space_count[b]];
						// // adds new spaces...
						// // loop `k--` happens.
						// It seems the kernel *skips* checking the swapped element.
						// Mimic exact kernel behavior: don't recheck `k`.
					}
				}

				// C. Prune Contained Spaces
				// Kernel logic
				for (int i = spaces.size() - 1; i >= 0; i--) {
					// Safety check index (java handles it via loop bound usually, but we modify
					// size inside)
					if (i >= spaces.size())
						continue;

					Space s1 = spaces.get(i);
					// Invalid dim check
					if (s1.w <= 0.0f || s1.h <= 0.0f || s1.d <= 0.0f) {
						int lastI = spaces.size() - 1;
						if (i != lastI)
							spaces.set(i, spaces.get(lastI));
						spaces.remove(lastI);
						// Mimic kernel: does not recheck `i`.
						continue;
					}

					boolean contained = false;
					for (int j = 0; j < spaces.size(); j++) {
						if (i == j)
							continue;
						if (isContained(s1, spaces.get(j))) {
							contained = true;
							break;
						}
					}

					if (contained) {
						int lastI = spaces.size() - 1;
						if (i != lastI)
							spaces.set(i, spaces.get(lastI));
						spaces.remove(lastI);
						// Mimic kernel behavior
					}
				}

			}

			// 3. New Bin
			if (!placed) {
				// Determine orientation for new bin (first that fits)
				int newBinOrientation = -1; // default
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
					if (w <= binTemplate.w && h <= binTemplate.h && d <= binTemplate.d) {
						newBinOrientation = o;
						break;
					}
				}

				if (newBinOrientation != -1) {
					float boxW = orientations[newBinOrientation][0];
					float boxH = orientations[newBinOrientation][1];
					float boxD = orientations[newBinOrientation][2];

					Bin newBin = new Bin(activeBins.size(), binTemplate.w, binTemplate.h, binTemplate.d,
							binTemplate.maxWeight);
					activeBins.add(newBin);
					List<Space> spaces = newBin.freeSpaces;
					spaces.clear(); // remove initial default space if any, we build manually like kernel

					// Add box
					box.size.x = boxW;
					box.size.y = boxH;
					box.size.z = boxD;
					box.position.x = 0;
					box.position.y = 0;
					box.position.z = 0;
					newBin.boxes.add(box);
					newBin.weight += box.weight;

					// Initial spaces (EMS style - maximal) around the box at 0,0,0
					// Right
					if (boxW < binTemplate.w) {
						spaces.add(new Space(boxW, 0.0f, 0.0f, binTemplate.w - boxW, binTemplate.h, binTemplate.d));
					}
					// Top
					if (boxH < binTemplate.h) {
						spaces.add(new Space(0.0f, boxH, 0.0f, binTemplate.w, binTemplate.h - boxH, binTemplate.d));
					}
					// Front
					if (boxD < binTemplate.d) {
						spaces.add(new Space(0.0f, 0.0f, boxD, binTemplate.w, binTemplate.h, binTemplate.d - boxD));
					}
				}
			}
		}

		return activeBins;
	}

	private boolean checkCollision(float bx, float by, float bz, float bw, float bh, float bd, Space s) {
		return (bx < s.x + s.w &&
				bx + bw > s.x &&
				by < s.y + s.h &&
				by + bh > s.y &&
				bz < s.z + s.d &&
				bz + bd > s.z);
	}

	private boolean isContained(Space s1, Space s2) {
		return (s1.x >= s2.x &&
				s1.y >= s2.y &&
				s1.z >= s2.z &&
				s1.x + s1.w <= s2.x + s2.w &&
				s1.y + s1.h <= s2.y + s2.h &&
				s1.z + s1.d <= s2.z + s2.d);
	}
}
