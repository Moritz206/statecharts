/* (c) https://github.com/MontiCore/monticore */
package de.monticore.scstatehierarchy;

import de.monticore.scbasis.StateCollector;
import de.monticore.scstatehierarchy._visitor.SCStateHierarchyVisitor;

/**
 * Extends the StateCollector for Hierarchical states
 */
public class HierarchicalStateCollector extends StateCollector implements SCStateHierarchyVisitor {

  protected SCStateHierarchyVisitor realThis= this;

  @Override
  public SCStateHierarchyVisitor getRealThis() {
    return realThis;
  }

  @Override
  public void setRealThis(SCStateHierarchyVisitor realThis) {
    this.realThis = realThis;
  }

}
