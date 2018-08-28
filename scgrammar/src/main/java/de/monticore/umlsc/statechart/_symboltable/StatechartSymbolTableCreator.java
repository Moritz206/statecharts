/* generated by template symboltable.SymbolTableCreator*/




package de.monticore.umlsc.statechart._symboltable;

import de.monticore.symboltable.ArtifactScope;
import de.monticore.symboltable.ImportStatement;
import de.monticore.symboltable.MutableScope;
import de.monticore.symboltable.ResolvingConfiguration;
import de.monticore.types.types._ast.ASTImportStatement;
import de.monticore.umlsc.statechart._ast.ASTSCArtifact;
import de.se_rwth.commons.Names;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

public class StatechartSymbolTableCreator extends StatechartSymbolTableCreatorTOP {

  public StatechartSymbolTableCreator(ResolvingConfiguration resolvingConfig, MutableScope enclosingScope) {
    super(resolvingConfig, enclosingScope);
  }

  public StatechartSymbolTableCreator(ResolvingConfiguration resolvingConfig, Deque<MutableScope> scopeStack) {
    super(resolvingConfig, scopeStack);
  }

  @Override
  protected MutableScope create_SCArtifact(ASTSCArtifact node) {
    final Optional<MutableScope> enclosingScope = Optional.ofNullable(getFirstCreatedScope());
    final String packageName = Names.getQualifiedName(node.getPackage());
    final List<ImportStatement> imports = getImportStatements(node);
    return new ArtifactScope(enclosingScope, packageName, imports);
  }

  private List<ImportStatement> getImportStatements(ASTSCArtifact node) {
    List<ImportStatement> imports = new ArrayList<>();
    if (node.getImportStatements() != null) {
      for (ASTImportStatement imp : node.getImportStatements()) {
        String qualifiedImport = Names.getQualifiedName(imp.getImportList());
        imports.add(new ImportStatement(qualifiedImport, imp.isStar()));
      }
    }
    return imports;
  }
}
