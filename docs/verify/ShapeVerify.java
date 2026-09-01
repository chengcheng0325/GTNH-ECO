import java.util.LinkedHashMap;
import java.util.Map;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Static verification of the E-Storage Array shape geometry (t30 revision: columns extend to the
 * RIGHT of the controller, ME bus stays at the back-side right corner).
 * Replays the exact shape generation from MTEEcoStorageArray.buildDefinitions and checks:
 *  1. '~' anchor sits at shape (A=n+2, B=1, C=0) for every size -> isContainedInStructure(n+2,1,0).
 *  2. Cell count per piece == 6(n+3) - 1 (2 C-slices x 3 B-lines x (n+3) A-chars, minus the
 *     controller skip); total element array length == cells + navigating elements.
 *  3. The iterateV2 world mapping (replayed with the exact StructureLib step/reset semantics,
 *     offsets (n+2,1,0), EAST-facing controller at (0,0,0)) places every cell with the columns
 *     extending RIGHT (+z = south): end cap at z=+n+2, columns at z=+2..+n+1 (drives at x=0,
 *     caps/vent at x=-1), controller at (0,0,0), head back plane at x=-1 with the ME bus at
 *     (-1,0,+1) (back-side right corner).
 */
public class ShapeVerify {

    static final int MAX_DRIVE_COLUMNS = 12;
    static final String PIECE_PREFIX = "size";

    static IStructureDefinition<String> build(int n) {
        StructureDefinition.Builder<String> builder = StructureDefinition.<String>builder()
            .addElement('C', StructureUtility.notAir())
            .addElement('D', StructureUtility.notAir())
            .addElement('E', StructureUtility.notAir())
            .addElement('V', StructureUtility.notAir())
            .addElement('M', StructureUtility.notAir());
        String driveRow = "C" + "D".repeat(n) + "CC";
        String driveMidRow = "C" + "D".repeat(n) + "C~";
        String capRow = "C" + "E".repeat(n) + "CC";
        String ventMidRow = "C" + "V".repeat(n) + "MC";
        String[][] slices = {
            { driveRow, driveMidRow, driveRow },
            { capRow, ventMidRow, capRow },
        };
        builder.addShape(PIECE_PREFIX + n, slices);
        return builder.build();
    }

    /** Replays StructureLib's iterateV2 navigation on the serialized element array (same step/reset
     *  semantics as StructureUtility.iterateV2, offsets = (offsetA, 1, 0)). */
    static Replay replayCells(IStructureDefinition<String> def, String piece, ExtendedFacing facing) {
        IStructureElement<String>[] els = def.getStructureFor(piece);
        int offsetA = Integer.parseInt(piece.substring("size".length())) + 2;
        int[] abc = new int[] { -offsetA, -1, 0 };
        int[] xyz = new int[3];
        int cellA = 0, cellB = 0, cellC = 0;
        int navigators = 0;
        Map<String, int[]> cells = new LinkedHashMap<>();
        for (IStructureElement<String> el : els) {
            if (el.isNavigating()) {
                navigators++;
                abc[0] = (el.resetA() ? -offsetA : abc[0]) + el.getStepA();
                abc[1] = (el.resetB() ? -1 : abc[1]) + el.getStepB();
                abc[2] = (el.resetC() ? 0 : abc[2]) + el.getStepC();
                if (el.resetB()) {
                    cellC++;
                    cellB = 0;
                    cellA = 0;
                } else if (el.resetA()) {
                    cellB++;
                    cellA = 0;
                }
                continue;
            }
            facing.getWorldOffset(abc, xyz);
            cells.put(cellA + "," + cellB + "," + cellC, new int[] { xyz[0], xyz[1], xyz[2] });
            abc[0] += 1;
            cellA++;
        }
        return new Replay(cells, navigators);
    }

    static final class Replay {
        final Map<String, int[]> cells;
        final int navigators;

        Replay(Map<String, int[]> cells, int navigators) {
            this.cells = cells;
            this.navigators = navigators;
        }
    }

    public static void main(String[] args) {
        int failures = 0;

        // 1) anchor + cell count for all sizes
        for (int n = 1; n <= MAX_DRIVE_COLUMNS; n++) {
            IStructureDefinition<String> def = build(n);
            if (!def.isContainedInStructure(PIECE_PREFIX + n, n + 2, 1, 0)) {
                System.out.println("FAIL size" + n + ": anchor (" + (n + 2) + ",1,0) not contained");
                failures++;
            }
            IStructureElement<String>[] els = def.getStructureFor(PIECE_PREFIX + n);
            int cellsExpected = 6 * (n + 3) - 1; // 2 x 3 x (n+3) minus the controller
            Replay replay = replayCells(def, PIECE_PREFIX + n, ExtendedFacing.of(ForgeDirection.EAST));
            if (replay.cells.size() != cellsExpected) {
                System.out.println("FAIL size" + n + ": cell count " + replay.cells.size() + " != " + cellsExpected);
                failures++;
            }
            if (els.length != replay.cells.size() + replay.navigators) {
                System.out.println(
                    "FAIL size" + n + ": elements " + els.length + " != cells " + replay.cells.size() + " + navigators "
                        + replay.navigators);
                failures++;
            }
        }
        System.out.println("anchor + element-count checks done, failures=" + failures);

        // 2) world mapping for EAST-facing, n=3 (columns to the right / south)
        int n = 3;
        ExtendedFacing facing = ExtendedFacing.of(ForgeDirection.EAST);
        Map<String, int[]> cells = replayCells(build(n), PIECE_PREFIX + n, facing).cells;

        // expected cells: {a,b,c, wx,wy,wz} for EAST-facing, n=3, controller at (0,0,0)
        // world = (-c, 1-b, n+2-a); a=0..5, b=0..2, c=0..1
        int[][] expected = {
            // C=0 plane (x=0): end cap (a=0, z=+5), columns D (a=1..3, z=+4..+2), head right (a=4, z=+1), controller casing (a=5, z=0)
            { 0, 0, 0, 0, 1, 5 }, { 0, 1, 0, 0, 0, 5 }, { 0, 2, 0, 0, -1, 5 },
            { 1, 0, 0, 0, 1, 4 }, { 1, 1, 0, 0, 0, 4 }, { 1, 2, 0, 0, -1, 4 },
            { 2, 0, 0, 0, 1, 3 }, { 2, 1, 0, 0, 0, 3 }, { 2, 2, 0, 0, -1, 3 },
            { 3, 0, 0, 0, 1, 2 }, { 3, 1, 0, 0, 0, 2 }, { 3, 2, 0, 0, -1, 2 },
            { 4, 0, 0, 0, 1, 1 }, { 4, 1, 0, 0, 0, 1 }, { 4, 2, 0, 0, -1, 1 },
            { 5, 0, 0, 0, 1, 0 }, { 5, 2, 0, 0, -1, 0 },
            // C=1 plane (x=-1): end cap (a=0, z=+5), columns E/V (a=1..3), head right with ME bus (a=4, z=+1), controller casing (a=5, z=0)
            { 0, 0, 1, -1, 1, 5 }, { 0, 1, 1, -1, 0, 5 }, { 0, 2, 1, -1, -1, 5 },
            { 1, 0, 1, -1, 1, 4 }, { 1, 1, 1, -1, 0, 4 }, { 1, 2, 1, -1, -1, 4 },
            { 2, 0, 1, -1, 1, 3 }, { 2, 1, 1, -1, 0, 3 }, { 2, 2, 1, -1, -1, 3 },
            { 3, 0, 1, -1, 1, 2 }, { 3, 1, 1, -1, 0, 2 }, { 3, 2, 1, -1, -1, 2 },
            { 4, 0, 1, -1, 1, 1 }, { 4, 1, 1, -1, 0, 1 }, { 4, 2, 1, -1, -1, 1 },
            { 5, 0, 1, -1, 1, 0 }, { 5, 1, 1, -1, 0, 0 }, { 5, 2, 1, -1, -1, 0 },
        };
        int cellFails = 0;
        for (int[] e : expected) {
            int[] got = cells.get(e[0] + "," + e[1] + "," + e[2]);
            if (got == null || got[0] != e[3] || got[1] != e[4] || got[2] != e[5]) {
                System.out.println("FAIL cell (" + e[0] + "," + e[1] + "," + e[2] + ") expected world ("
                    + e[3] + "," + e[4] + "," + e[5] + ") got "
                    + (got == null ? "null" : "(" + got[0] + "," + got[1] + "," + got[2] + ")"));
                cellFails++;
            }
        }
        if (cells.containsKey("5,1,0")) {
            System.out.println("FAIL controller anchor cell (5,1,0) unexpectedly present as element");
            cellFails++;
        }
        // ME bus must sit at the back-side right corner (-1, 0, +1) for EAST
        int[] meBus = cells.get("4,1,1");
        if (meBus == null || meBus[0] != -1 || meBus[1] != 0 || meBus[2] != 1) {
            System.out.println("FAIL ME bus at (4,1,1) not at back-side right corner (-1,0,+1), got "
                + (meBus == null ? "null" : "(" + meBus[0] + "," + meBus[1] + "," + meBus[2] + ")"));
            cellFails++;
        }
        System.out.println("world-mapping cell checks done, failures=" + cellFails + ", cells recorded=" + cells.size());
        System.out.println((failures + cellFails) == 0 ? "ALL CHECKS PASSED" : "CHECKS FAILED");
        if (failures + cellFails > 0) System.exit(1);
    }
}
