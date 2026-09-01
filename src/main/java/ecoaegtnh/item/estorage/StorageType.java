package ecoaegtnh.item.estorage;

/**
 * t100: storage family type — item / fluid / essentia cells, components and housings share the
 * same three-way split (matching the cell items' getCellBaseName() labels).
 */
public enum StorageType {

    ITEM("item"),
    FLUID("fluid"),
    ESSENTIA("essentia");

    /** Lang/texture suffix ("item", "fluid", "essentia"). */
    public final String label;

    StorageType(String label) {
        this.label = label;
    }
}
