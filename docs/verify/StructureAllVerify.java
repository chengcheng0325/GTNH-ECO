import java.util.HashMap;
import java.util.Map;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Definitive all-length verification of the E-Storage Array structure (t30 shape, t32 base):
 *
 *  A) For EVERY size n = 1..12:
 *     1. '~' anchor contained at (A=n+2, B=1, C=0).
 *     2. Serialized element count == cells + navigators (replay consistency).
 *     3. EVERY replayed cell's world position matches the shape->world formula
 *        (EAST facing, offsets (n+2,1,0), base (0,0,0)) — cell-by-cell, no spot checks.
 *     4. ME bus cell (A=n+1,B=1,C=1) sits at world (-1,0,+1) for every n (back-side right corner).
 *     5. The end-cap column A=0 sits at z=n+2 (both C planes, all 3 heights).
 *  B) Full cross-matrix: for every BUILT length B = 1..12, a synthetic world built as the player
 *     would place it (per the shape with offsets (B+2,1,0)) must match EXACTLY ONE size — the
 *     descending checkMachine loop must find S == B and no other size may match.
 */
public class StructureAllVerify {

    static final int MAX_DRIVE_COLUMNS = 12;
    static final String PIECE_PREFIX = "size";

    static String repeat(char ch, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, ch);
        return new String(chars);
    }

    static String[][] shapeFor(int n) {
        String driveRow = "C" + repeat('D', n) + "CC";
        String driveMidRow = "C" + repeat('D', n) + "C~";
        String capRow = "C" + repeat('E', n) + "CC";
        String ventMidRow = "C" + repeat('V', n) + "MC";
        return new String[][] {
            { driveRow, driveMidRow, driveRow },
            { capRow, ventMidRow, capRow },
        };
    }

    static IStructureDefinition<String> buildDef(int n) {
        StructureDefinition.Builder<String> builder = StructureDefinition.<String>builder()
            .addElement('C', StructureUtility.notAir())
            .addElement('D', StructureUtility.notAir())
            .addElement('E', StructureUtility.notAir())
            .addElement('V', StructureUtility.notAir())
            .addElement('M', StructureUtility.notAir());
        builder.addShape(PIECE_PREFIX + n, shapeFor(n));
        return builder.build();
    }

    /** Replays StructureLib's iterateV2 on the REAL serialized element array. */
    static Replay replay(IStructureDefinition<String> def, String piece, ExtendedFacing facing) {
        IStructureElement<String>[] els = def.getStructureFor(piece);
        int offsetA = Integer.parseInt(piece.substring(PIECE_PREFIX.length())) + 2;
        int[] abc = new int[] { -offsetA, -1, 0 };
        int[] xyz = new int[3];
        int cellA = 0, cellB = 0, cellC = 0;
        int navigators = 0;
        Map<String, int[]> cells = new HashMap<>();
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
        return new Replay(cells, navigators, els.length);
    }

    static final class Replay {
        final Map<String, int[]> cells;
        final int navigators;
        final int total;

        Replay(Map<String, int[]> cells, int navigators, int total) {
            this.cells = cells;
            this.navigators = navigators;
            this.total = total;
        }
    }

    /** Builds the world map of a structure with nBuild columns, exactly as placed by the player
     *  (offset anchor (nBuild+2,1,0), EAST-facing controller at (0,0,0)). */
    static Map<String, Character> buildWorld(int nBuild) {
        ExtendedFacing facing = ExtendedFacing.of(ForgeDirection.EAST);
        String[][] shape = shapeFor(nBuild);
        Map<String, Character> world = new HashMap<>();
        int[] abc = new int[3];
        int[] xyz = new int[3];
        for (int c = 0; c <= 1; c++) {
            for (int b = 0; b <= 2; b++) {
                for (int a = 0; a <= nBuild + 2; a++) {
                    char ch = shape[c][b].charAt(a);
                    if (ch == '~') continue;
                    abc[0] = a - (nBuild + 2);
                    abc[1] = b - 1;
                    abc[2] = c;
                    facing.getWorldOffset(abc, xyz);
                    world.put(xyz[0] + "," + xyz[1] + "," + xyz[2], ch);
                }
            }
        }
        return world;
    }

    /** Replays one structure check exactly like checkMachine: piece sizeS, offsets (S+2,1,0). */
    static boolean checkShape(int s, Map<String, Character> world) {
        IStructureDefinition<String> def = buildDef(s);
        ExtendedFacing facing = ExtendedFacing.of(ForgeDirection.EAST);
        IStructureElement<String>[] els = def.getStructureFor(PIECE_PREFIX + s);
        String[][] shape = shapeFor(s);
        int offsetA = s + 2;
        int[] abc = new int[] { -offsetA, -1, 0 };
        int[] xyz = new int[3];
        int cellA = 0, cellB = 0, cellC = 0;
        for (IStructureElement<String> el : els) {
            if (el.isNavigating()) {
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
            char expected = shape[cellC][cellB].charAt(cellA);
            if (expected != '~') {
                facing.getWorldOffset(abc, xyz);
                Character actual = world.get(xyz[0] + "," + xyz[1] + "," + xyz[2]);
                if (actual == null || actual != expected) {
                    return false;
                }
            }
            abc[0] += 1;
            cellA++;
        }
        return true;
    }

    public static void main(String[] args) {
        int failures = 0;
        ExtendedFacing east = ExtendedFacing.of(ForgeDirection.EAST);

        // A) per-size full cell verification
        for (int n = 1; n <= MAX_DRIVE_COLUMNS; n++) {
            IStructureDefinition<String> def = buildDef(n);
            String piece = PIECE_PREFIX + n;
            if (!def.isContainedInStructure(piece, n + 2, 1, 0)) {
                System.out.println("FAIL size" + n + ": anchor (" + (n + 2) + ",1,0) not contained");
                failures++;
            }
            Replay rp = replay(def, piece, east);
            int expectedCells = 6 * (n + 3) - 1;
            if (rp.cells.size() != expectedCells) {
                System.out.println("FAIL size" + n + ": cells " + rp.cells.size() + " != " + expectedCells);
                failures++;
            }
            if (rp.total != rp.cells.size() + rp.navigators) {
                System.out.println(
                    "FAIL size" + n + ": elements " + rp.total + " != cells " + rp.cells.size() + " + nav "
                        + rp.navigators);
                failures++;
            }
            String[][] shape = shapeFor(n);
            int[] abc = new int[3];
            int[] xyz = new int[3];
            // cell-by-cell world verification: every shape cell -> expected world per formula
            for (int c = 0; c <= 1; c++) {
                for (int b = 0; b <= 2; b++) {
                    for (int a = 0; a <= n + 2; a++) {
                        char ch = shape[c][b].charAt(a);
                        if (ch == '~') {
                            if (rp.cells.containsKey(a + "," + b + "," + c)) {
                                System.out.println("FAIL size" + n + ": controller cell (" + a + "," + b + "," + c
                                    + ") unexpectedly present");
                                failures++;
                            }
                            continue;
                        }
                        int[] got = rp.cells.get(a + "," + b + "," + c);
                        if (got == null) {
                            System.out.println("FAIL size" + n + ": cell (" + a + "," + b + "," + c + ") missing");
                            failures++;
                            continue;
                        }
                        abc[0] = a - (n + 2);
                        abc[1] = b - 1;
                        abc[2] = c;
                        east.getWorldOffset(abc, xyz);
                        if (got[0] != xyz[0] || got[1] != xyz[1] || got[2] != xyz[2]) {
                            System.out.println("FAIL size" + n + ": cell (" + a + "," + b + "," + c + ") expected world ("
                                + xyz[0] + "," + xyz[1] + "," + xyz[2] + ") got (" + got[0] + "," + got[1] + "," + got[2]
                                + ")");
                            failures++;
                        }
                    }
                }
            }
            // ME bus at the back-side right corner for every n
            int[] meBus = rp.cells.get((n + 1) + ",1,1");
            if (meBus == null || meBus[0] != -1 || meBus[1] != 0 || meBus[2] != 1) {
                System.out.println("FAIL size" + n + ": ME bus not at (-1,0,+1), got "
                    + (meBus == null ? "null" : "(" + meBus[0] + "," + meBus[1] + "," + meBus[2] + ")"));
                failures++;
            }
            // end cap at z=n+2 (both planes)
            for (int b = 0; b <= 2; b++) {
                for (int c = 0; c <= 1; c++) {
                    int[] cap = rp.cells.get("0," + b + "," + c);
                    if (cap == null || cap[0] != -c || cap[2] != n + 2) {
                        System.out.println("FAIL size" + n + ": cap cell (0," + b + "," + c + ") wrong: "
                            + (cap == null ? "null" : "(" + cap[0] + "," + cap[1] + "," + cap[2] + ")"));
                        failures++;
                    }
                }
            }
            System.out.println("size" + n + ": anchor/count/cells/ME-bus/cap OK (cells=" + rp.cells.size() + ")");
        }

        // B) full cross-matrix: built length B must match exactly size B
        for (int b = 1; b <= MAX_DRIVE_COLUMNS; b++) {
            Map<String, Character> world = buildWorld(b);
            int matches = 0;
            int matched = -1;
            for (int s = 1; s <= MAX_DRIVE_COLUMNS; s++) {
                if (checkShape(s, world)) {
                    matches++;
                    matched = s;
                }
            }
            if (matches != 1 || matched != b) {
                System.out.println("FAIL build=" + b + ": matched sizes=" + matches + " (last=" + matched + ")");
                failures++;
            } else {
                System.out.println("build=" + b + " columns -> ONLY size" + matched + " matches");
            }
        }
        System.out.println((failures == 0) ? "ALL STRUCTURE CHECKS PASSED" : "CHECKS FAILED");
        if (failures > 0) System.exit(1);
    }
}
