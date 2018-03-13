package com.huntloc.aqt.equipmenttracking;


public class Type {
    private int TypeId;
    private String Description;

    public Type(String Description, int TypeId) {
        this.Description = Description;
        this.TypeId = TypeId;
    }

    public int getTypeId() {
        return TypeId;
    }

    public void setTypeId(int typeId) {
        TypeId = typeId;
    }

    public String getDescription() {
        return Description;
    }

    public void setDescription(String description) {
        Description = description;
    }

    public String toString() {
        return Description;
    }
}
