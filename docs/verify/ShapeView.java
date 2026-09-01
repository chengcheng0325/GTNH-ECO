import java.util.LinkedHashMap;
import java.util.Map;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Renders the E-Storage Array structure as a top-down ASCII map (y=0 slice) for each of the four
 * horizontal facings, using the exact shape generation + iterateV2 step semantics from
 * MTEEcoStorageArray.buildDefinitions. Ground truth for the t30 projection-direction report:
 * the drive columns extend to the RIGHT of the controller (the facing's right-hand side) for every
 * facing, and the ME bus sits at the controller's back-side right corner.
 *
 * Cell legend: ~ controller, D drive, E capacitance, V vent, M ME bus, C casing, . empty.
 * The map is centered on the controller at (0,0); rows are z (north at top), columns are x
 * (west at left), so the printout is the player's top-down view.
 */
public class ShapeView {

    static final int MAX_DRIVE_COLUMNS = 12;

    static String[][] shapeFor(int n) {
        String driveRow = "C" + "D".repeat(n) + "CC";
        String driveMidRow = "C" + "D".repeat(n) + "C~";
        String capRow = "C" + "E".repeat(n) + "CC";
        String ventMidRow = "C" + "V".repeat(n) + "MC";
        return new String[][] {
            { driveRow, driveMidRow, driveRow },
            { capRow, ventMidRow, capRow },
        };
    }

    static IStructureDefinition<String> build(int n) {
        StructureDefinition.Builder<String> builder = StructureDefinition.<String>builder()
            .addElement('C', StructureUtility.notAir())
            .addElement('D', StructureUtility.notAir())
            .addElement('E', StructureUtility.notAir())
            .addElement('V', StructureUtility.notAir())
            .addElement('M', StructureUtility.notAir());
        builder.addShape("size" + n, shapeFor(n));
        return builder.build();
    }

    static Map<String, Character> computeCells(IStructureDefinition<String> def, String piece, ExtendedFacing facing) {
        IStructureElement<String>[] els = def.getStructureFor(piece);
        int offsetA = Integer.parseInt(piece.substring("size".length())) + 2;
        int[] abc = new int[] { -offsetA, -1, 0 };
        int[] xyz = new int[3];
        int cellA = 0, cellB = 0, cellC = 0;
        Map<String, Character> cells = new LinkedHashMap<>();
        String[][] shape = shapeFor(Integer.parseInt(piece.substring("size".length())));
        for (IStructureElement<String> el : els) {
            if (el.isNavigating()) {
                abc[0] = (el.resetA() ? -offsetA : abc[0]) + el.getStepA();
                abc[1] = (el.resetB() ? -1 : abc[1]) + el.getStepB();
                abc[2] = (el.resetC() ? 0 : abc[2]) + el.getStepC();
                if (el.resetB()) {
                    // stepC (between C slices): resetA+resetB
                    cellC++;
                    cellB = 0;
                    cellA = 0;
                } else if (el.resetA()) {
                    // stepB (between B lines): resets A only
                    cellB++;
                    cellA = 0;
                }
                continue;
            }
            facing.getWorldOffset(abc, xyz);
            char ch = shape[cellC][cellB].charAt(cellA);
            cells.put(xyz[0] + "," + xyz[1] + "," + xyz[2], ch);
            abc[0] += 1;
            cellA++;
        }
        return cells;
    }

    public static void main(String[] args) {
        int n = 3; // match the DESIGN ASCII example
        IStructureDefinition<String> def = build(n);
        ForgeDirection[] dirs = { ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.EAST, ForgeDirection.WEST };
        for (ForgeDirection dir : dirs) {
            ExtendedFacing facing = ExtendedFacing.of(dir);
            Map<String, Character> cells = computeCells(def, "size" + n, facing);
            // auto-fit the y=0 window to the structure plus a 2-block margin
            int minX = 0, maxX = 0, minZ = 0, maxZ = 0;
            for (String key : cells.keySet()) {
                if (!key.endsWith(",0,")) {
                    String[] parts = key.split(",");
                    int x = Integer.parseInt(parts[0]);
                    int z = Integer.parseInt(parts[2]);
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                }
            }
            minX -= 2;
            maxX += 2;
            minZ -= 2;
            maxZ += 2;
            System.out.println("=== controller facing " + dir.name() + " (front = " + dir.name() + ") ===");
            StringBuilder header = new StringBuilder("     ");
            for (int x = minX; x <= maxX; x++) {
                header.append(String.format("%+2d ", x));
            }
            System.out.println(header.toString().trim());
            for (int z = maxZ; z >= minZ; z--) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("z=%+2d  ", z));
                for (int x = minX; x <= maxX; x++) {
                    Character ch = cells.get(x + ",0," + z);
                    if (ch == null) {
                        sb.append(x == 0 && z == 0 ? "X" : ".");
                    } else if (ch == '~') {
                        sb.append('~');
                    } else {
                        sb.append(ch);
                    }
                }
                System.out.println(sb.toString());
            }
            System.out.println();
        }
    }
}
