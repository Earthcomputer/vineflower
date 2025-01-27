package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles pattern matching for instanceof in statements.
 *
 * @author SuperCoder79
 */
public final class PatternMatchProcessor {
  public static boolean matchInstanceof(RootStatement root) {
    return matchInstanceofRec(root, root);
  }

  private static boolean matchInstanceofRec(Statement statement, RootStatement root) {
    for (Statement stat : statement.getStats()) {
      if (matchInstanceofRec(stat, root)) {
        return true;
      }
    }

    if (statement instanceof IfStatement) {
      return handleIf((IfStatement) statement, root);
    }

    return false;
  }

  private static boolean handleIf(IfStatement statement, RootStatement root) {
    Exprent condition = statement.getHeadexprent().getCondition();

    if (condition.type != Exprent.EXPRENT_FUNCTION) {
      return false;
    }

    FunctionExprent func = (FunctionExprent) condition;

    List<Exprent> exprents = func.getAllExprents(true);

    // TODO: need to properly analyze the scope around instanceof to handle negations

    boolean updated = false;
    loop:
    for (Exprent exprent : exprents) {
      if (exprent.type == Exprent.EXPRENT_FUNCTION) {
        FunctionExprent iof = (FunctionExprent)exprent;

        // Check for instanceof
        if (iof.getFuncType() == FunctionExprent.FUNCTION_INSTANCEOF) {
          Exprent source = iof.getLstOperands().get(0);
          Exprent target = iof.getLstOperands().get(1);

          // Check to make sure there are more than 1 exprent.
          // More often than not, when there's less than 1 it means it's assigning into a previous value.
          // TODO: this isn't always the case, handle it properly
          if (statement.getIfstat() != null && statement.getIfstat().getExprents() != null && statement.getIfstat().getExprents().size() > 1) {
            Exprent first = statement.getIfstat().getExprents().get(0);

            // Check inside of the if statement for a cast
            if (first.type == Exprent.EXPRENT_ASSIGNMENT) {
              // If it's an assignement, get both sides
              Exprent left = first.getAllExprents().get(0);
              Exprent right = first.getAllExprents().get(1);

              // Right side needs to be a cast function
              if (right.type == Exprent.EXPRENT_FUNCTION) {
                if (((FunctionExprent)right).getFuncType() == FunctionExprent.FUNCTION_CAST) {
                  Exprent casted = right.getAllExprents().get(0);

                  // Check if the exprent being casted is the exprent on the left side of the instanceof
                  if (source.equals(casted)) {
                    // Make sure the left hand side is a variable and it's type matches the target of the cast
                    if (left.type == Exprent.EXPRENT_VAR && target.getExprType().equals(left.getExprType())) {
                      List<VarVersionPair> vvs = new ArrayList<>();

                      // We need to make sure we're not assigning to previously assigned variables.
                      // This gets all predecessors of the if statement and gathers all the variable assignments inside.
                      // TODO: cache this
                      findVarsInPredecessors(vvs, statement.getIfstat());

                      VarVersionPair var = ((VarExprent) left).getVarVersionPair();

                      // Stop processing if this variable has already been seen
                      for (VarVersionPair vv : vvs) {
                        if (var.var == vv.var) {
                          continue loop;
                        }
                      }

                      // Add the exprent to the instanceof exprent and remove it from the inside of the if statement
                      iof.getLstOperands().add(2, left);
                      statement.getIfstat().getExprents().remove(0);

                      updated = true;
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    return updated;
  }

  // Finds all assignments and their associated variables in a statement's predecessors.
  private static void findVarsInPredecessors(List<VarVersionPair> vvs, Statement root) {
    for (StatEdge pred : root.getAllPredecessorEdges()) {
      Statement stat = pred.getSource();

      if (stat.getExprents() != null) {
        for (Exprent exprent : stat.getExprents()) {

          // Check for assignment exprents
          if (exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
            AssignmentExprent assignment = (AssignmentExprent) exprent;

            // If the left type of the assignment is a variable, store it's var info
            if (assignment.getLeft().type == Exprent.EXPRENT_VAR) {
              vvs.add(((VarExprent) assignment.getLeft()).getVarVersionPair());
            }
          }
        }
      }
    }
  }
}
