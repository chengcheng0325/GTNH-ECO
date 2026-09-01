import java.util.HashMap;
import java.util.Map;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Simulates the full checkMachine loop of MTEEcoStorageArray (t30 shape): for every built column
 * length 1..12, build a synthetic "world" exactly matching that structure, then replay the
 * descending shape check (size12..size1 with offsets (n+2,1,0)) exactly as checkMachine does, and
 * assert that the FIRST match is the built length and no other length matches.
 *
 * This proves the "only longest forms" report is not a shape/offset bug: for a build of length L,
 * size12..size(L+1) must all fail and sizeL must pass.
 */
public class LengthVerify {

    static final int MAX_DRIVE_COLUMNS = 12;

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

    static String repeat(char ch, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, ch);
        return new String(chars);
    }

    static IStructureDefinition<String> buildDef(int n) {
        StructureDefinition.Builder<String> builder = StructureDefinition.<String>builder()
            .addElement('C', StructureUtility.notAir())
            .addElement('D', StructureUtility.notAir())
            .addElement('E', StructureUtility.notAir())
            .addElement('V', StructureUtility.notAir())
            .addElement('M', StructureUtility.notAir());
        builder.addShape("size" + n, shapeFor(n));
        return builder.build();
    }

    /** Builds the world map (world key "x,y,z" -> char) of a structure with `nBuild` columns,
     *  EAST-facing controller at (0,0,0), placing blocks at the exact world positions the shape
     *  maps to (offsets (nBuild+2, 1, 0), like scanStructureVolume). */
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
                    if (ch == '~') continue; // the controller itself is not a placed block
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

    /** Replays one structure check exactly like checkMachine: piece sizeN with offsets (n+2,1,0). */
    static boolean checkShape(int n, Map<String, Character> world) {
        IStructureDefinition<String> def = buildDef(n);
        ExtendedFacing facing = ExtendedFacing.of(ForgeDirection.EAST);
        IStructureElement<String>[] els = def.getStructureFor("size" + n);
        String[][] shape = shapeFor(n);
        int offsetA = n + 2;
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
            if (expected != '~') { // skip the controller anchor
                facing.getWorldOffset(abc, xyz);
                Character actual = world.get(xyz[0] + "," + xyz[1] + "," + xyz[2]);
                if (actual == null || actual != expected) {
                    return false; // cell missing or wrong block
                }
            }
            abc[0] += 1;
            cellA++;
        }
        return true;
    }

    public static void main(String[] args) {
        int failures = 0;
        for (int nBuild = 1; nBuild <= MAX_DRIVE_COLUMNS; nBuild++) {
            Map<String, Character> world = buildWorld(nBuild);
            int matched = -1;
            for (int n = MAX_DRIVE_COLUMNS; n >= 1; n--) {
                if (checkShape(n, world)) {
                    matched = n;
                    break;
                }
            }
            if (matched != nBuild) {
                System.out.println("FAIL build=" + nBuild + " columns matched size" + matched);
                failures++;
            } else {
                System.out.println("build=" + nBuild + " columns -> matched size" + matched + " OK");
            }
        }
        System.out.println((failures == 0) ? "ALL LENGTHS CHECKED PASSED" : "CHECKS FAILED");
        if (failures > 0) System.exit(1);
    }
}
