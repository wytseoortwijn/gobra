// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.
//
// Copyright (c) 2011-2020 ETH Zurich.

package viper.gobra

import org.scalatest.{FunSuite, Inside, Matchers}
import viper.gobra.ast.frontend._
import viper.gobra.frontend.Config
import viper.gobra.frontend.info.Info
import viper.gobra.frontend.info.base.Type
import viper.gobra.frontend.info.implementation.TypeInfoImpl

class ExprTypingUnitTests extends FunSuite with Matchers with Inside {
  val frontend = new TestFrontend()

  test("TypeChecker: should classify an integer literal as integer") {
    frontend.exprType(PIntLit(42))() should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should not classify integer literals as ghost") {
    assert(!frontend.isGhostExpr(PIntLit(42))())
  }

  test("TypeChecker: should classify a Boolean literal as Boolean") {
    frontend.exprType(PBoolLit(false))() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: should not classify Boolean literals as ghost") {
    assert(!frontend.isGhostExpr(PBoolLit(true))())
  }

  test("TypeChecker: should classify a named operand by its type") {
    val inArgs = Vector(PNamedParameter(PIdnDef("x"), PIntType(), false))
    frontend.exprType(PNamedOperand(PIdnUse("x")))(inArgs) should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should classify a ghost input parameter as being ghost") {
    val inArgs = Vector(PExplicitGhostParameter(PNamedParameter(PIdnDef("x"), PIntType(), false)))
    assert (frontend.isGhostExpr(PNamedOperand(PIdnUse("x")))(inArgs))
  }

  test("TypeChecker: should classify a sequence literal as ghost") {
    val expr = PLiteral.sequence(PBoolType(), Vector())
    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should correctly type a Boolean sequence") {
    val expr = PLiteral.sequence(PBoolType(), Vector())

    frontend.exprType(expr)() should matchPattern {
      case Type.SequenceT(Type.BooleanT) =>
    }
  }

  test("TypeChecker: should classify an sequence indexed expression as ghost") {
    val base = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
        PKeyedElement(None, PExpCompositeVal(PIntLit(2)))
      ))
    )

    val expr = PIndexedExp(base, PIntLit(0))

    assert(frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should correctly type a simple sequence indexed expression") {
    val base = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
        PKeyedElement(None, PExpCompositeVal(PIntLit(2)))
      ))
    )

    val expr = PIndexedExp(base, PIntLit(0))

    frontend.exprType(expr)() should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: mark a sequence indexed expression with an incorrect left-hand side as not well-defined") {
    val expr = PIndexedExp(PIntLit(42), PIntLit(0))
    assert(!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: mark a sequence indexed expression with an incorrect right-hand side as not well-defined") {
    val base = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
        PKeyedElement(None, PExpCompositeVal(PIntLit(2)))
      ))
    )

    val expr = PIndexedExp(base, PBoolLit(false))

    assert(!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type chained sequence indexing expressions") {
    val inArgs = Vector(
      PExplicitGhostParameter(
        PNamedParameter(PIdnDef("xs"), PSequenceType(PSequenceType(PIntType())), false)
      )
    )
    val expr = PIndexedExp(
      PIndexedExp(PNamedOperand(PIdnUse("xs")), PIntLit(2)),
      PIntLit(4)
    )
    frontend.exprType(expr)(inArgs) should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should classify a sequence slice expression as ghost") {
    val inArgs = Vector(
      PExplicitGhostParameter(
        PNamedParameter(PIdnDef("xs"), PSequenceType(PIntType()), false)
      )
    )
    val expr = PSliceExp(
      PNamedOperand(PIdnUse("xs")),
      Some(PIntLit(2)),
      Some(PIntLit(4)),
      None
    )
    frontend.isGhostExpr(expr)(inArgs) should matchPattern {
      case true =>
    }
  }

  test("TypeChecker: should classify a proper sequence slice expression as well-defined") {
    val base = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
        PKeyedElement(None, PExpCompositeVal(PIntLit(2)))
      ))
    )

    val expr = PSliceExp(base, Some(PIntLit(2)), Some(PIntLit(4)), None)

    frontend.wellDefExpr(expr)().valid should matchPattern {
      case true =>
    }
  }

  test("TypeChecker: should not classify a sequence slice expression with a capacity as being well-defined") {
    val base = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
        PKeyedElement(None, PExpCompositeVal(PIntLit(2)))
      ))
    )

    val expr = PSliceExp(base, Some(PIntLit(2)), Some(PIntLit(4)), Some(PIntLit(6)))

    frontend.wellDefExpr(expr)().valid should matchPattern {
      case false =>
    }
  }

  test("TypeChecker: should classify a proper sequence slice expression with a missing 'low' index as well-defined") {
    val base = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
        PKeyedElement(None, PExpCompositeVal(PIntLit(2)))
      ))
    )

    val expr = PSliceExp(base, None, Some(PIntLit(4)), None)

    frontend.wellDefExpr(expr)().valid should matchPattern {
      case true =>
    }
  }

  test("TypeChecker: should classify a proper sequence slice expression with a missing 'high' index as well-defined") {
    val base = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
        PKeyedElement(None, PExpCompositeVal(PIntLit(2)))
      ))
    )

    val expr = PSliceExp(base, Some(PIntLit(1)), None, None)

    frontend.wellDefExpr(expr)().valid should matchPattern {
      case true =>
    }
  }

  test("TypeChecker: should classify a proper sequence slice expression with a missing 'low' and 'high' index as well-defined") {
    val base = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
        PKeyedElement(None, PExpCompositeVal(PIntLit(2)))
      ))
    )

    val expr = PSliceExp(base, None, None, None)

    frontend.wellDefExpr(expr)().valid should matchPattern {
      case true =>
    }
  }

  test("TypeChecker: should not allow the 'low' index of a slice expression to be anything other than an integer") {
    val base = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
        PKeyedElement(None, PExpCompositeVal(PIntLit(2)))
      ))
    )

    val expr = PSliceExp(base, Some(PBoolLit(false)), Some(PIntLit(2)), None)

    frontend.wellDefExpr(expr)().valid should matchPattern {
      case false =>
    }
  }

  test("TypeChecker: should not allow the 'high' index of a slice expression to be anything other than an integer") {
    val base = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
        PKeyedElement(None, PExpCompositeVal(PIntLit(2)))
      ))
    )

    val expr = PSliceExp(base, Some(PIntLit(2)), Some(PBoolLit(false)), None)

    frontend.wellDefExpr(expr)().valid should matchPattern {
      case false =>
    }
  }

  test("TypeChecker: should correctly identify the type of a Boolean sequence slice expression") {
    val base = PCompositeLit(
      PSequenceType(PBoolType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PBoolLit(true))),
        PKeyedElement(None, PExpCompositeVal(PBoolLit(false)))
      ))
    )

    val expr = PSliceExp(base, Some(PIntLit(1)), Some(PIntLit(4)), None)

    frontend.exprType(expr)() should matchPattern {
      case Type.SequenceT(Type.BooleanT) =>
    }
  }

  test("TypeChecker: should correctly identify the type of a nested sequence slice expression") {
    val base = PCompositeLit(
      PSequenceType(PSequenceType(PIntType())),
      PLiteralValue(Vector())
    )

    val expr = PSliceExp(base, Some(PIntLit(1)), Some(PIntLit(4)), None)

    frontend.exprType(expr)() should matchPattern {
      case Type.SequenceT(Type.SequenceT(Type.IntT)) =>
    }
  }

  test("TypeChecker: should classify an (integer) set literal as ghost") {
    val expr = PLiteral.set(PIntType(), Vector())
    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should correctly type an (empty) integer set literal") {
    val expr = PLiteral.set(PIntType(), Vector())
    frontend.exprType(expr)() should matchPattern {
      case Type.SetT(Type.IntT) =>
    }
  }

  test("TypeChecker: should correctly type a nested (empty) set literal") {
    val expr = PLiteral.set(PSetType(PBoolType()), Vector())
    frontend.exprType(expr)() should matchPattern {
      case Type.SetT(Type.SetT(Type.BooleanT)) =>
    }
  }

  test("TypeChecker: should classify empty Boolean set literal as well-defined") {
    val expr = PLiteral.set(PIntType(), Vector())
    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a singleton (integer) set literal with wrong subexpressions") {
    val expr = PLiteral.set(PIntType(), Vector(PBoolLit(false)))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a set literal with (right and) wrong members") {
    val expr = PLiteral.set(PIntType(), Vector(
      PIntLit(1),
      PIntLit(5),
      PBoolLit(false),
      PIntLit(7)
    ))

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type a non-empty set literal") {
    val expr = PLiteral.set(PIntType(), Vector(
      PIntLit(1),
      PIntLit(5),
      PIntLit(7)
    ))

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should classify a set union operation as ghost") {
    val expr = PUnion(
      PLiteral.set(PBoolType(), Vector()),
      PLiteral.set(PBoolType(), Vector()),
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should type check a set union with operands of matching type") {
    val expr = PUnion(
      PLiteral.set(PIntType(), Vector(PIntLit(2))),
      PLiteral.set(PIntType(), Vector(PIntLit(4), PIntLit(5))),
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type a set union operation") {
    val expr = PUnion(
      PLiteral.set(PIntType(), Vector(PIntLit(2))),
      PLiteral.set(PIntType(), Vector(PIntLit(4), PIntLit(5))),
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.SetT(Type.IntT) =>
    }
  }

  test("TypeChecker: should not type check the union of a set and sequence") {
    val expr = PUnion(
      PLiteral.set(PIntType(), Vector(PIntLit(2))),
      PCompositeLit(
        PSequenceType(PIntType()),
        PLiteralValue(Vector(
          PKeyedElement(None, PExpCompositeVal(PIntLit(4))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(5)))
        ))
      )
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a union of two integers") {
    val expr = PUnion(
      PIntLit(42),
      PIntLit(22)
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should type check a chain of unions") {
    val expr = PUnion(
      PLiteral.set(PIntType(), Vector(PIntLit(2))),
      PUnion(
        PLiteral.set(PIntType(), Vector(PIntLit(4))),
        PLiteral.set(PIntType(), Vector(PIntLit(5))),
      )
    )

    assert (frontend.wellDefExpr(expr)().valid)

    frontend.exprType(expr)() should matchPattern {
      case Type.SetT(Type.IntT) =>
    }
  }

  test("TypeChecker: should classify set intersection as ghost") {
    val expr = PIntersection(
      PLiteral.set(PBoolType(), Vector(PBoolLit(true))),
      PLiteral.set(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should let an intersection of two Boolean sets be well-defined") {
    val expr = PIntersection(
      PLiteral.set(PBoolType(), Vector(PBoolLit(true))),
      PLiteral.set(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not let the intersection of a Boolean set and an integer set be well-defined") {
    val expr = PIntersection(
      PLiteral.set(PBoolType(), Vector(PBoolLit(true))),
      PLiteral.set(PIntType(), Vector(PIntLit(42)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type an integer set intersection") {
    val expr = PIntersection(
      PLiteral.set(PIntType(), Vector(PIntLit(1), PIntLit(2))),
      PLiteral.set(PIntType(), Vector(PIntLit(3)))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.SetT(Type.IntT) =>
    }
  }

  test("TypeChecker: should classify set difference as ghost") {
    val expr = PSetMinus(
      PLiteral.set(PBoolType(), Vector(PBoolLit(true))),
      PLiteral.set(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should let the difference of two Boolean sets be well-defined") {
    val expr = PSetMinus(
      PLiteral.set(PBoolType(), Vector(PBoolLit(true))),
      PLiteral.set(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not let the difference of a Boolean set and an integer set be well-defined") {
    val expr = PSetMinus(
      PLiteral.set(PBoolType(), Vector(PBoolLit(true))),
      PLiteral.set(PIntType(), Vector(PIntLit(42)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type an integer set difference") {
    val expr = PSetMinus(
      PLiteral.set(PIntType(), Vector(PIntLit(1), PIntLit(2))),
      PLiteral.set(PIntType(), Vector(PIntLit(3)))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.SetT(Type.IntT) =>
    }
  }

  test("TypeChecker: should mark any use of the subset relation as ghost") {
    val expr = PSubset(
      PLiteral.set(PIntType(), Vector(PIntLit(1), PIntLit(2))),
      PLiteral.set(PIntType(), Vector(PIntLit(3)))
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should mark a normal use of the subset relation as well-defined") {
    val expr = PSubset(
      PLiteral.set(PIntType(), Vector(PIntLit(1), PIntLit(2))),
      PLiteral.set(PIntType(), Vector(PIntLit(3)))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should assign the expected type to the result of a subset expression (1)") {
    val expr = PSubset(
      PLiteral.set(PIntType(), Vector(PIntLit(1), PIntLit(2))),
      PLiteral.set(PIntType(), Vector(PIntLit(3)))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: should assign the expected type to the result of a subset expression (2)") {
    val expr = PSubset(
      PLiteral.set(PBoolType(), Vector()),
      PLiteral.set(PBoolType(), Vector(PBoolLit(false)))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: should not mark the use of a subset relation with incompatible operands as well-defined") {
    val expr = PSubset(
      PLiteral.set(PIntType(), Vector(PIntLit(3))),
      PLiteral.set(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not mark a subset relation well-defined if its operand is not well-defined") {
    val expr = PSubset(
      PLiteral.set(PBoolType(), Vector(PIntLit(42))),
      PLiteral.set(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should classify any use of a sequence append as ghost") {
    val expr = PSequenceAppend(
      PCompositeLit(PSequenceType(PIntType()), PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(1)))
      ))),
      PCompositeLit(PSequenceType(PIntType()), PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(2)))
      )))
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should classify any proper use of a sequence append as well-defined") {
    val expr = PSequenceAppend(
      PCompositeLit(PSequenceType(PIntType()), PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(1)))
      ))),
      PCompositeLit(PSequenceType(PIntType()), PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(2)))
      )))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type a sequence append of integers") {
    val expr = PSequenceAppend(
      PCompositeLit(PSequenceType(PIntType()), PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(1)))
      ))),
      PCompositeLit(PSequenceType(PIntType()), PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(2)))
      )))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.SequenceT(Type.IntT) =>
    }
  }

  test("TypeChecker: should not just typecheck a chain of subsets") {
    val expr = PSubset(
      PSubset(
        PLiteral.set(PIntType(), Vector()),
        PLiteral.set(PIntType(), Vector())
      ),
      PLiteral.set(PIntType(), Vector())
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should classify any use of a sequence inclusion as ghost") {
    val expr = PIn(
      PIntLit(2),
      PCompositeLit(
        PSequenceType(PIntType()),
        PLiteralValue(Vector(
          PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(2))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(3)))
        ))
      )
    )
    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should classify any proper use of a sequence inclusion as being well-defined") {
    val expr = PIn(
      PIntLit(2),
      PCompositeLit(
        PSequenceType(PIntType()),
        PLiteralValue(Vector(
          PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(2))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(3)))
        ))
      )
    )
    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a sequence inclusion as being well-defined if the types don't match") {
    val expr = PIn(
      PBoolLit(false),
      PCompositeLit(
        PSequenceType(PIntType()),
        PLiteralValue(Vector(
          PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(2))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(3)))
        ))
      )
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a seq/set inclusion as well-defined if the right-hand side is not a sequence of (multi)set") {
    val expr = PIn(PBoolLit(false), PBoolLit(true))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should assign the correct type to a sequence inclusion operation") {
    val expr = PIn(
      PIntLit(2),
      PCompositeLit(
        PSequenceType(PIntType()),
        PLiteralValue(Vector(
          PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(2))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(3)))
        ))
      )
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: should classify any use of a set inclusion as ghost") {
    val expr = PIn(
      PIntLit(2),
      PLiteral.set(PIntType(), Vector(PIntLit(1), PIntLit(2), PIntLit(3)))
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should classify any proper use of a set inclusion as being well-defined") {
    val expr = PIn(
      PIntLit(2),
      PLiteral.set(PIntType(), Vector(PIntLit(1), PIntLit(2), PIntLit(3)))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should assign the correct type to a set inclusion operation") {
    val expr = PIn(
      PIntLit(2),
      PLiteral.set(PIntType(), Vector(PIntLit(1), PIntLit(2), PIntLit(3)))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: should classify a sequence length operator as ghost") {
    val expr = PLength(
      PCompositeLit(
        PSequenceType(PIntType()),
        PLiteralValue(Vector(
          PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(2))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(3)))
        ))
      )
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should classify the sequence length operator as well-defined when applied to a proper sequence") {
    val expr = PLength(
      PCompositeLit(
        PSequenceType(PIntType()),
        PLiteralValue(Vector(
          PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(2))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(3)))
        ))
      )
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should assign the correct type to a sequence length operator") {
    val expr = PLength(
      PCompositeLit(
        PSequenceType(PIntType()),
        PLiteralValue(Vector(
          PKeyedElement(None, PExpCompositeVal(PIntLit(1))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(2))),
          PKeyedElement(None, PExpCompositeVal(PIntLit(3)))
        ))
      )
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should not typecheck any seq/set size operator when no sequence of (multi)set is provided") {
    val expr = PCardinality(PIntLit(42))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should classify a set cardinality operator as ghost") {
    val expr = PCardinality(
      PLiteral.set(PIntType(), Vector(PIntLit(1), PIntLit(2), PIntLit(3)))
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should classify the set cardinality operator as well-defined when given a proper set") {
    val expr = PCardinality(
      PLiteral.set(PIntType(), Vector(PIntLit(1), PIntLit(2), PIntLit(3)))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should assign the correct type to a set cardinality operator") {
    val expr = PCardinality(
      PLiteral.set(PIntType(), Vector(PIntLit(1), PIntLit(2), PIntLit(3)))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should not type check the intersection of two Booleans") {
    val expr = PIntersection(PBoolLit(false), PBoolLit(true))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check the set difference of two Booleans") {
    val expr = PSetMinus(PBoolLit(false), PBoolLit(true))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check the subset of two Booleans") {
    val expr = PSubset(PBoolLit(false), PBoolLit(true))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should classify a simple empty Boolean multiset literal as ghost") {
    val expr = PLiteral.multiset(PBoolType(), Vector())
    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should let a simple empty integer multiset literal be well-defined") {
    val expr = PLiteral.multiset(PIntType(), Vector())
    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should let a simple empty multiset literal of a nested type be well-defined") {
    val expr = PLiteral.multiset(PMultisetType(PBoolType()), Vector())
    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type an empty Boolean multiset literal") {
    val expr = PLiteral.multiset(PBoolType(), Vector())
    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.BooleanT) =>
    }
  }

  test("TypeChecker: should correctly type an empty multiset literal of a nested type") {
    val expr = PLiteral.multiset(PMultisetType(PIntType()), Vector())
    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.MultisetT(Type.IntT)) =>
    }
  }

  test("TypeChecker: should classify a singleton integer multiset literal as ghost") {
    val expr = PLiteral.multiset(PIntType(), Vector(PIntLit(42)))
    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should classify a simple singleton integer multiset literal as well-defined") {
    val expr = PLiteral.multiset(PIntType(), Vector(PIntLit(42)))
    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not let a singleton multiset literal be well-defined if the types do not match") {
    val expr = PLiteral.multiset(PIntType(), Vector(PBoolLit(false)))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should classify a non-empty integer multiset literal as ghost") {
    val expr = PLiteral.multiset(PIntType(), Vector(
      PIntLit(1), PIntLit(2), PIntLit(3)
    ))

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should classify a proper non-empty integer multiset literal as well-defined") {
    val expr = PLiteral.multiset(PIntType(), Vector(
      PIntLit(1), PIntLit(2), PIntLit(3)
    ))

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should classify a non-empty multiset literal with incorrect types as not well-defined (1)") {
    val expr = PLiteral.multiset(PIntType(), Vector(
      PIntLit(1), PIntLit(3), PBoolLit(false)
    ))

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should classify a non-empty multiset literal with incorrect types as not well-defined (2)") {
    val expr = PLiteral.multiset(PSetType(PIntType()), Vector(
      PLiteral.sequence(PIntType(), Vector())
    ))

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type a non-empty integer multiset literal") {
    val expr = PLiteral.multiset(PIntType(), Vector(
      PIntLit(1), PIntLit(2), PIntLit(3)
    ))

    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.IntT) =>
    }
  }

  test("TypeChecker: should correctly type a nested multiset literal") {
    val expr = PLiteral.multiset(PMultisetType(PBoolType()), Vector(
      PLiteral.multiset(PBoolType(), Vector())
    ))

    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.MultisetT(Type.BooleanT)) =>
    }
  }

  test("TypeChecker: should classify the union of the multiset integer literals as well-defined") {
    val expr = PUnion(
      PLiteral.multiset(PIntType(), Vector(PIntLit(1))),
      PLiteral.multiset(PIntType(), Vector(PIntLit(2)))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a union of a multiset and a set as well-defined") {
    val expr = PUnion(
      PLiteral.multiset(PIntType(), Vector(PIntLit(1))),
      PLiteral.set(PIntType(), Vector(PIntLit(2)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a union of a multiset and a sequence as well-defined") {
    val expr = PUnion(
      PLiteral.sequence(PIntType(), Vector(PIntLit(1))),
      PLiteral.multiset(PIntType(), Vector(PIntLit(2)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify the union of two incompatible multisets as well-defined") {
    val expr = PUnion(
      PLiteral.multiset(PIntType(), Vector(PIntLit(1))),
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a union of multisets as well-defined if there is a typing error in the left operand") {
    val expr = PUnion(
      PLiteral.multiset(PIntType(), Vector(PBoolLit(true))),
      PLiteral.multiset(PIntType(), Vector(PIntLit(2)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a union of multisets as well-defined if there is a typing error in the right operand") {
    val expr = PUnion(
      PLiteral.multiset(PIntType(), Vector(PIntLit(2))),
      PLiteral.multiset(PIntType(), Vector(PBoolLit(false)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type the union of two integer multisets") {
    val expr = PUnion(
      PLiteral.multiset(PIntType(), Vector()),
      PLiteral.multiset(PIntType(), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.IntT) =>
    }
  }

  test("TypeChecker: should correctly type the union of two nested multiset literals") {
    val expr = PUnion(
      PLiteral.multiset(PMultisetType(PBoolType()), Vector()),
      PLiteral.multiset(PMultisetType(PBoolType()), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.MultisetT(Type.BooleanT)) =>
    }
  }

  test("TypeChecker: should classify the intersection of two multiset integer literals as well-defined") {
    val expr = PIntersection(
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(true))),
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify an intersection of a multiset and a set as well-defined") {
    val expr = PIntersection(
      PLiteral.multiset(PIntType(), Vector(PIntLit(1))),
      PLiteral.set(PIntType(), Vector(PIntLit(2)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify an intersection of a multiset and a sequence as well-defined") {
    val expr = PIntersection(
      PLiteral.sequence(PIntType(), Vector(PIntLit(1))),
      PLiteral.multiset(PIntType(), Vector(PIntLit(2)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify the intersection of two incompatible multisets as well-defined") {
    val expr = PIntersection(
      PLiteral.multiset(PIntType(), Vector(PIntLit(1))),
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify an intersection of multisets as well-defined if there is a typing error in the left operand") {
    val expr = PIntersection(
      PLiteral.multiset(PIntType(), Vector(PBoolLit(true))),
      PLiteral.multiset(PIntType(), Vector(PIntLit(2)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify an intersection of multisets as well-defined if there is a typing error in the right operand") {
    val expr = PIntersection(
      PLiteral.multiset(PIntType(), Vector(PIntLit(2))),
      PLiteral.multiset(PIntType(), Vector(PBoolLit(false)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type the intersection of two integer multisets") {
    val expr = PIntersection(
      PLiteral.multiset(PIntType(), Vector()),
      PLiteral.multiset(PIntType(), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.IntT) =>
    }
  }

  test("TypeChecker: should correctly type the intersection of two nested multiset literals") {
    val expr = PIntersection(
      PLiteral.multiset(PMultisetType(PBoolType()), Vector()),
      PLiteral.multiset(PMultisetType(PBoolType()), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.MultisetT(Type.BooleanT)) =>
    }
  }

  test("TypeChecker: should classify the set difference of two multiset integer literals as well-defined") {
    val expr = PSetMinus(
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(false))),
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(true), PBoolLit(false)))
    )
    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a set difference of a multiset and a set as well-defined") {
    val expr = PSetMinus(
      PLiteral.multiset(PIntType(), Vector(PIntLit(1))),
      PLiteral.set(PIntType(), Vector(PIntLit(2)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a set difference of a multiset and a sequence as well-defined") {
    val expr = PSetMinus(
      PLiteral.sequence(PIntType(), Vector(PIntLit(1))),
      PLiteral.multiset(PIntType(), Vector(PIntLit(2)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify the set difference of two incompatible multisets as well-defined") {
    val expr = PSetMinus(
      PLiteral.multiset(PIntType(), Vector(PIntLit(1))),
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a set difference of multisets as well-defined if there is a typing error in the left operand") {
    val expr = PSetMinus(
      PLiteral.multiset(PIntType(), Vector(PBoolLit(true))),
      PLiteral.multiset(PIntType(), Vector(PIntLit(2)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a set difference of multisets as well-defined if there is a typing error in the right operand") {
    val expr = PSetMinus(
      PLiteral.multiset(PIntType(), Vector(PIntLit(2))),
      PLiteral.multiset(PIntType(), Vector(PBoolLit(false)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type the set difference of two integer multisets") {
    val expr = PSetMinus(
      PLiteral.multiset(PIntType(), Vector()),
      PLiteral.multiset(PIntType(), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.IntT) =>
    }
  }

  test("TypeChecker: should correctly type the set difference of two nested multiset literals") {
    val expr = PSetMinus(
      PLiteral.multiset(PMultisetType(PBoolType()), Vector()),
      PLiteral.multiset(PMultisetType(PBoolType()), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.MultisetT(Type.BooleanT)) =>
    }
  }

  test("TypeChecker: should classify a use of the subset relation between multisets as ghost") {
    val expr = PSubset(
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(true))),
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should classify a standard use of the subset relation between two multisets as well-defined") {
    val expr = PSubset(
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(true))),
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a subset of multisets as well-defined if the left operand is a set instead of a multiset") {
    val expr = PSubset(
      PLiteral.set(PBoolType(), Vector(PBoolLit(true))),
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a subset of multisets as well-defined if the right operand is a set instead of a multiset") {
    val expr = PSubset(
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(true))),
      PLiteral.set(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a subset of multisets as well-defined if there is a type error in the left operand") {
    val expr = PSubset(
      PLiteral.multiset(PBoolType(), Vector(PIntLit(12))),
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a subset of multisets as well-defined if there is a type error in the right operand") {
    val expr = PSubset(
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(true))),
      PLiteral.multiset(PBoolType(), Vector(PIntLit(24)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type a subset relation of two incompatible multisets") {
    val expr = PSubset(
      PLiteral.multiset(PIntType(), Vector(PIntLit(12))),
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should be able to correctly type a subset relation of Boolean multiset literals") {
    val expr = PSubset(
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(true))),
      PLiteral.multiset(PBoolType(), Vector(PBoolLit(false)))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: must correctly type a subset of two type-nested multiset literals") {
    val expr = PSubset(
      PLiteral.multiset(PMultisetType(PIntType()), Vector()),
      PLiteral.multiset(PMultisetType(PIntType()), Vector())
    )
    frontend.exprType(expr)() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: should mark the use of a multiset cardinality as ghost") {
    val expr = PCardinality(PLiteral.multiset(PBoolType(), Vector(PBoolLit(false))))
    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should classify a normal use of the multiset cardinality as well-defined") {
    val expr = PCardinality(PLiteral.multiset(PBoolType(), Vector(PBoolLit(false))))
    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a multiset cardinality as well-defined if there is a typing error in the operand") {
    val expr = PCardinality(PLiteral.multiset(PBoolType(), Vector(PIntLit(42))))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should classify the cardinality of a nested multiset as well-defined") {
    val expr = PCardinality(
      PLiteral.multiset(PMultisetType(PIntType()), Vector(
        PLiteral.multiset(PIntType(), Vector(PIntLit(42)))
      ))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type a standard use of the multiset cardinality") {
    val expr = PCardinality(PLiteral.multiset(PBoolType(), Vector(PBoolLit(false))))

    frontend.exprType(expr)() should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should correctly type a 'nested' multiset cardinality") {
    val expr = PCardinality(
      PLiteral.multiset(PMultisetType(PIntType()), Vector(
        PLiteral.multiset(PIntType(), Vector(PIntLit(42)))
      ))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should mark a simple multiset inclusion expression as ghost") {
    val expr = PIn(
      PIntLit(42),
      PLiteral.multiset(PIntType(), Vector())
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should classify a simple multiset inclusion expression as well-defined") {
    val expr = PIn(
      PIntLit(42),
      PLiteral.multiset(PIntType(), Vector())
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a multiset inclusion as well-defined if the types of the operands aren't compatible") {
    val expr = PIn(
      PBoolLit(false),
      PLiteral.multiset(PIntType(), Vector())
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a multiset inclusion operation as well-defined if there is a typing problem in the left operand") {
    val expr = PIn(
      PLiteral.multiset(PIntType(), Vector(PBoolLit(false))),
      PLiteral.multiset(PMultisetType(PIntType()), Vector())
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a multiset inclusion operation as well-defined if there is a typing problem in the right operand") {
    val expr = PIn(
      PBoolLit(false),
      PLiteral.multiset(PBoolType(), Vector(PIntLit(42)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify a multiset inclusion operation as well-defined if it mixed multisets with ordinary sets") {
    val expr = PIn(
      PLiteral.multiset(PIntType(), Vector()),
      PLiteral.multiset(PSetType(PIntType()), Vector())
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type a simple multiset inclusion operation") {
    val expr = PIn(
      PIntLit(2),
      PLiteral.multiset(PIntType(), Vector(PIntLit(1), PIntLit(2), PIntLit(3)))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: should correctly type a slightly more complicated multiset inclusion operation") {
    val expr = PIn(
      PLiteral.multiset(PIntType(), Vector(PIntLit(42))),
      PLiteral.multiset(PMultisetType(PIntType()), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: should not type a (multi)set inclusion with integer literals as operands") {
    val expr = PIn(PIntLit(1), PIntLit(2))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should be able to handle a comparison of multiset inclusions") {
    val expr = PEquals(
      PIn(PIntLit(2), PLiteral.multiset(PIntType(), Vector(PIntLit(2)))),
      PIn(PIntLit(3), PLiteral.multiset(PIntType(), Vector(PIntLit(4))))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should be able to correctly type a comparison of multiset inclusions") {
    val expr = PEquals(
      PIn(PIntLit(2), PLiteral.multiset(PIntType(), Vector(PIntLit(2)))),
      PIn(PIntLit(3), PLiteral.multiset(PIntType(), Vector(PIntLit(4))))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: should be able to correctly type a comparison of (multi)set union") {
    val expr = PEquals(
      PUnion(
        PLiteral.multiset(PIntType(), Vector(PIntLit(1))),
        PLiteral.multiset(PIntType(), Vector(PIntLit(2)))
      ),
      PUnion(
        PLiteral.multiset(PIntType(), Vector(PIntLit(2))),
        PLiteral.multiset(PIntType(), Vector(PIntLit(4)))
      )
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: should be able to correctly type a comparison of (multi)set intersection") {
    val expr = PEquals(
      PIntersection(
        PLiteral.multiset(PIntType(), Vector(PIntLit(1))),
        PLiteral.multiset(PIntType(), Vector(PIntLit(2)))
      ),
      PIntersection(
        PLiteral.multiset(PIntType(), Vector(PIntLit(2))),
        PLiteral.multiset(PIntType(), Vector(PIntLit(4)))
      )
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: should be able to correctly type a comparison of (multi)set difference") {
    val expr = PEquals(
      PSetMinus(
        PLiteral.multiset(PIntType(), Vector(PIntLit(1))),
        PLiteral.multiset(PIntType(), Vector(PIntLit(2)))
      ),
      PSetMinus(
        PLiteral.multiset(PIntType(), Vector(PIntLit(2))),
        PLiteral.multiset(PIntType(), Vector(PIntLit(4)))
      )
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: should classify the conversion of a sequence to a set as ghost") {
    val expr = PSetConversion(PRangeSequence(PIntLit(1), PIntLit(10)))
    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should classify the conversion of a sequence range to a set as well-defined") {
    val expr = PSetConversion(PRangeSequence(PIntLit(1), PIntLit(10)))
    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify the (explicit) conversion of an integer to a set as well-defined") {
    val expr = PSetConversion(PIntLit(42))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should classify the explicit conversion of a set to a set as well-defined") {
    val expr = PSetConversion(
      PLiteral.set(PBoolType(), Vector(PBoolLit(false)))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not classify an explicit set conversion as well-defined if there is a typing error in the inner expression") {
    val expr = PSetConversion(
      PLiteral.set(PBoolType(), Vector(PIntLit(42)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should let two nested explicit set conversions be well-defined") {
    val expr = PSetConversion(
      PSetConversion(PLiteral.sequence(PIntType(), Vector()))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check the set conversion of a Boolean literal") {
    val expr = PSetConversion(PBoolLit(false))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check the nested set conversion of a Boolean literal") {
    val expr = PSetConversion(PSetConversion(PBoolLit(false)))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type a simple explicit conversion expression from a sequence (range) to a set") {
    val expr = PSetConversion(
      PRangeSequence(PIntLit(1), PIntLit(100))
    )
    frontend.exprType(expr)() should matchPattern {
      case Type.SetT(Type.IntT) =>
    }
  }

  test("TypeChecker: should correctly type a simple explicit conversion from a set to a set") {
    val expr = PSetConversion(PLiteral.set(PBoolType(), Vector()))

    frontend.exprType(expr)() should matchPattern {
      case Type.SetT(Type.BooleanT) =>
    }
  }

  test("TypeChecker: should correctly type a simple nested explicit set conversion expression") {
    val expr = PSetConversion(
      PSetConversion(PLiteral.sequence(PIntType(), Vector()))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.SetT(Type.IntT) =>
    }
  }

  test("TypeChecker: should classify the sequence multiplicity operator as ghost") {
    val expr = PMultiplicity(
      PIntLit(42),
      PLiteral.sequence(PIntType(), Vector())
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should classify a very simple use of the seq multiplicity operator as well-defined") {
    val expr = PMultiplicity(
      PIntLit(42),
      PLiteral.sequence(PIntType(), Vector())
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a multiplicity operator if the right-hand side is not a sequence but for example an integer") {
    val expr = PMultiplicity(
      PIntLit(2),
      PIntLit(3)
    )
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a sequence multiplicity operator if the types of the operands don't match") {
    val expr = PMultiplicity(
      PIntLit(42),
      PLiteral.sequence(PBoolType(), Vector())
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should let a slightly more complex proper use of the sequence multiplicity operator be well-defined") {
    val expr = PMultiplicity(
      PLiteral.sequence(PBoolType(), Vector(PBoolLit(false))),
      PLiteral.sequence(PSequenceType(PBoolType()), Vector())
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should let a simple use of the sequence multiplicity operator be of type integer") {
    val expr = PMultiplicity(
      PIntLit(42),
      PLiteral.sequence(PIntType(), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should let a slightly more complex use of the sequence multiplicity operator be of type integer") {
    val expr = PMultiplicity(
      PLiteral.sequence(PBoolType(), Vector(PBoolLit(false))),
      PLiteral.sequence(PSequenceType(PBoolType()), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should classify a (proper) multiset conversion of a multiset as ghost") {
    val expr = PMultisetConversion(
      PLiteral.multiset(PBoolType(), Vector())
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should type check a standard multiset conversion of a multiset") {
    val expr = PMultisetConversion(
      PLiteral.multiset(PIntType(), Vector())
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should type check a slightly more involved multiset conversion of a multiset") {
    val expr = PMultisetConversion(
      PUnion(
        PLiteral.multiset(PIntType(), Vector(PIntLit(1))),
        PLiteral.multiset(PIntType(), Vector(PIntLit(2), PIntLit(3)))
      )
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should type check the union of two multiset conversions of multisets") {
    val expr = PUnion(
      PMultisetConversion(PLiteral.multiset(PIntType(), Vector())),
      PMultisetConversion(PLiteral.multiset(PIntType(), Vector()))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a multiset conversion of something other than a collection") {
    val expr = PMultisetConversion(PBoolLit(false))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a multiset conversion of a multiset if there is a typing problem in the operand") {
    val expr = PMultisetConversion(
      PUnion(
        PLiteral.multiset(PIntType(), Vector()),
        PLiteral.multiset(PBoolType(), Vector())
      )
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check the union of the results of two incompatible multiset conversions of multisets") {
    val expr = PUnion(
      PMultisetConversion(PLiteral.multiset(PIntType(), Vector())),
      PMultisetConversion(PLiteral.multiset(PBoolType(), Vector()))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should classify a (proper) multiset conversion of a sequence as ghost") {
    val expr = PMultisetConversion(
      PLiteral.sequence(PBoolType(), Vector())
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should type check a simple proper multiset conversion of a sequence") {
    val expr = PMultisetConversion(
      PLiteral.sequence(PBoolType(), Vector())
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should type check a slightly more complex multiset conversion expression of a sequence") {
    val expr = PMultisetConversion(
      PSequenceAppend(
        PLiteral.sequence(PIntType(), Vector(PIntLit(1))),
        PLiteral.sequence(PIntType(), Vector(PIntLit(2), PIntLit(3)))
      )
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type check an mset union of the results of two multiset conversions of sequences") {
    val expr = PUnion(
      PMultisetConversion(PLiteral.sequence(PIntType(), Vector(PIntLit(1)))),
      PMultisetConversion(PLiteral.sequence(PIntType(), Vector(PIntLit(2))))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check an multiset conversion of sequences with an incorrect body expression") {
    val expr = PMultisetConversion(
      PSequenceAppend(
        PLiteral.sequence(PIntType(), Vector()),
        PLiteral.sequence(PBoolType(), Vector())
      )
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check the union of two incompatible results of multiset conversions of sequences") {
    val expr = PUnion(
      PMultisetConversion(PLiteral.sequence(PIntType(), Vector())),
      PMultisetConversion(PLiteral.sequence(PBoolType(), Vector()))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should assign the correct type to a simple multiset conversion of a multiset") {
    val expr = PMultisetConversion(
      PLiteral.multiset(PSequenceType(PIntType()), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.SequenceT(Type.IntT)) =>
    }
  }

  test("TypeChecker: should assign the correct type to a simple multiset conversion of a sequence") {
    val expr = PMultisetConversion(
      PLiteral.sequence(PMultisetType(PBoolType()), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.MultisetT(Type.BooleanT)) =>
    }
  }

  test("TypeChecker: should correctly type the union of two matching multiset conversions") {
    val expr = PUnion(
      PMultisetConversion(PLiteral.sequence(PBoolType(), Vector())),
      PMultisetConversion(PLiteral.sequence(PBoolType(), Vector())),
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.BooleanT) =>
    }
  }

  test("TypeChecker: should not type check the multiset conversion of a multiset literal that has a typing problem") {
    val expr = PMultisetConversion(
      PLiteral.multiset(PBoolType(), Vector(PIntLit(42)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check the multiset conversion of a sequence literal that has a typing problem") {
    val expr = PMultisetConversion(
      PLiteral.sequence(PIntType(), Vector(PBoolLit(false)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a set conversion of a multiset (yet)") {
    val expr = PSetConversion(
      PLiteral.multiset(PIntType(), Vector())
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a multiset conversion of an ordinary set (yet)") {
    val expr = PMultisetConversion(PLiteral.set(PBoolType(), Vector()))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should mark the multiset multiplicity operator as ghost") {
    val expr = PMultiplicity(
      PIntLit(42),
      PLiteral.multiset(PIntType(), Vector())
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should correctly type check a simple use of the multiset multiplicity operator") {
    val expr = PMultiplicity(
      PIntLit(42),
      PLiteral.multiset(PIntType(), Vector())
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should type check a slightly more complex use of the multiset multiplicity operator") {
    val expr = PMultiplicity(
      PLiteral.set(PBoolType(), Vector(PBoolLit(false))),
      PLiteral.multiset(PSetType(PBoolType()), Vector())
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a multiset multiplicity operator if the types of the operands don't match") {
    val expr = PMultiplicity(
      PIntLit(42),
      PLiteral.multiset(PBoolType(), Vector())
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a multiset multiplicity operator if there is a typing error in the left operand") {
    val expr = PMultiplicity(
      PLiteral.set(PIntType(), Vector(PBoolLit(false))),
      PLiteral.multiset(PSetType(PIntType()), Vector())
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a use of the multiset multiplicity operator if there is a typing error in the right operand") {
    val expr = PMultiplicity(
      PIntLit(42),
      PLiteral.multiset(PIntType(), Vector(PBoolLit(true)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type check a small chain of multiset multiplicity operators") {
    val expr = PMultiplicity(
      PMultiplicity(
        PBoolLit(false),
        PLiteral.multiset(PBoolType(), Vector())
      ),
      PLiteral.multiset(PIntType(), Vector())
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should assign the correct type to a simple multiset multiplicity operator") {
    val expr = PMultiplicity(
      PIntLit(42),
      PLiteral.multiset(PIntType(), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should assign the correct type to a slightly more complicated use of the multiset multiplicity operator") {
    val expr = PMultiplicity(
      PLiteral.set(PBoolType(), Vector(PBoolLit(false))),
      PLiteral.multiset(PSetType(PBoolType()), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should mark the multiset inclusion operator as ghost") {
    val expr = PIn(PIntLit(42), PLiteral.multiset(PIntType(), Vector()))
    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should let a simple use of the multiset inclusion operator be well-defined") {
    val expr = PIn(PIntLit(42), PLiteral.multiset(PIntType(), Vector()))
    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not let a multiset inclusion operator be well-defined if the types do not match") {
    val expr = PIn(
      PIntLit(42),
      PLiteral.multiset(PBoolType(), Vector())
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a multiset inclusion operator if there is a typing error in the left operand") {
    val expr = PIn(
      PLiteral.set(PBoolType(), Vector(PIntLit(42))),
      PLiteral.multiset(PSetType(PBoolType()), Vector())
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a multiset inclusion operator if there is a typing error in the right operand") {
    val expr = PIn(
      PLiteral.set(PBoolType(), Vector(PBoolLit(false))),
      PLiteral.multiset(PSetType(PBoolType()), Vector(PIntLit(42)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should let the type of a multiset inclusion operator be Boolean (instead of integer)") {
    val expr = PIn(
      PIntLit(42),
      PLiteral.multiset(PIntType(), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: should correctly type a small chain of multiset inclusions") {
    val expr = PIn(
      PIn(
        PIntLit(42),
        PLiteral.multiset(PIntType(), Vector())
      ),
      PLiteral.multiset(PBoolType(), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.BooleanT =>
    }
  }

  test("TypeChecker: should classify the set multiplicity operator as ghost") {
    val expr = PMultiplicity(
      PIntLit(12),
      PLiteral.set(PIntType(), Vector())
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should let a standard use of the multiplicity operator on sets be well-defined") {
    val expr = PMultiplicity(
      PIntLit(12),
      PLiteral.set(PIntType(), Vector())
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a set multiplicity operator if the types of the operands don't match") {
    val expr = PMultiplicity(
      PIntLit(12),
      PLiteral.set(PBoolType(), Vector())
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a set multiplicity operator if there is a typing problem in the left operand") {
    val expr = PMultiplicity(
      PLiteral.sequence(PBoolType(), Vector(PIntLit(42))),
      PLiteral.set(PSequenceType(PBoolType()), Vector())
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a set multiplicity operator if there is a typing problem in the right operand") {
    val expr = PMultiplicity(
      PLiteral.sequence(PBoolType(), Vector(PBoolLit(false))),
      PLiteral.set(PSequenceType(PBoolType()), Vector(PIntLit(42)))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should let a simple use of a set multiplicity operator be of type integer") {
    val expr = PMultiplicity(
      PIntLit(12),
      PLiteral.set(PIntType(), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should correctly type a slightly more involved use of the set multiplicity operator") {
    val expr = PMultiplicity(
      PLiteral.sequence(PBoolType(), Vector(PBoolLit(false))),
      PLiteral.set(PSequenceType(PBoolType()), Vector())
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should classify the use of 'len' on sequences as ghost") {
    val expr = PLength(PLiteral.sequence(PBoolType(), Vector()))
    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should be able to type a very simple use of the built-in 'len' function applied on sequences") {
    val expr = PLength(PLiteral.sequence(PBoolType(), Vector()))
    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type a 'len' function application on an integer") {
    val expr = PLength(PIntLit(42))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type a 'len' function application on, say, a Boolean") {
    val expr = PLength(PBoolLit(false))
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should be able to type check a slightly more complex application of 'len' on sequences") {
    val expr = PLength(
      PSequenceAppend(
        PRangeSequence(PIntLit(1), PIntLit(10)),
        PLiteral.sequence(PIntType(), Vector(PIntLit(42)))
      )
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a use of the 'len' function if there is a typing error in its argument") {
    val expr = PLength(
      PSequenceAppend(
        PRangeSequence(PIntLit(1), PIntLit(10)),
        PLiteral.sequence(PBoolType(), Vector())
      )
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should be able to type the addition of two applications of 'len' to sequences") {
    val expr = PAdd(
      PLength(PRangeSequence(PIntLit(1), PIntLit(10))),
      PLength(PLiteral.sequence(PBoolType(), Vector(PBoolLit(false))))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should assign the correct type to a simple application of 'len' to a sequence (of some type)") {
    val expr = PLength(PLiteral.sequence(PBoolType(), Vector()))

    frontend.exprType(expr)() should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should assign the correct type to a slightly more complex application of 'len'") {
    val expr = PLength(
      PSequenceAppend(
        PRangeSequence(PIntLit(1), PIntLit(10)),
        PLiteral.sequence(PIntType(), Vector(PIntLit(42)))
      )
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.IntT =>
    }
  }

  test("TypeChecker: should not type check a cardinality operation applied on a sequence") {
    val expr = PCardinality(
      PLiteral.sequence(PBoolType(), Vector())
    )
    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should let a sequence range expression be pure") {
    val expr = PRangeSequence(PIntLit(1), PIntLit(10))
    assert (frontend.isPureExpr(expr)())
  }

  test("TypeChecker: should let a sequence drop operation be pure") {
    val expr = PSliceExp(
      PLiteral.sequence(PBoolType(), Vector()),
      None,
      Some(PIntLit(42)),
      None
    )

    assert (frontend.isPureExpr(expr)())
  }

  test("TypeChecker: should let a sequence take operation be pure") {
    val expr = PSliceExp(
      PLiteral.sequence(PBoolType(), Vector()),
      Some(PIntLit(42)),
      None,
      None
    )

    assert (frontend.isPureExpr(expr)())
  }

  test("TypeChecker: should let a complete sequence slice expression be marked as pure") {
    val expr = PSliceExp(
      PLiteral.sequence(PBoolType(), Vector()),
      Some(PIntLit(2)),
      Some(PIntLit(8)),
      None
    )

    assert (frontend.isPureExpr(expr)())
  }

  test("TypeChecker: should let a simple (integer) sequence index operation be marked a pure") {
    val expr = PIndexedExp(
      PLiteral.sequence(PIntType(), Vector(PIntLit(1), PIntLit(2), PIntLit(3))),
      PIntLit(2)
    )

    assert (frontend.isPureExpr(expr)())
  }

  test("TypeChecker: should let an empty integer sequence literal be well-defined") {
    val expr = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector())
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should let a simple integer sequence literal be well-defined") {
    val expr = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(42)))
      ))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should mark a simple integer sequence literal as ghost") {
    val expr = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(42)))
      ))
    )

    assert (frontend.isGhostExpr(expr)())
  }

  test("TypeChecker: should mark a simple integer sequence literal as pure") {
    val expr = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(42)))
      ))
    )

    assert (frontend.isPureExpr(expr)())
  }

  test("TypeChecker: should correctly type a simple integer sequence literal") {
    val expr = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(42)))
      ))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.SequenceT(Type.IntT) =>
    }
  }

  test("TypeChecker: should let a singleton (sequence) composite literal with a valid key be well-defined") {
    val expr = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(
          Some(PExpCompositeVal(PIntLit(0))),
          PExpCompositeVal(PIntLit(42))
        )
      ))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not let a composite (sequence) literal with a key be well-defined if the key is negative") {
    val expr = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(
          Some(PExpCompositeVal(PIntLit(-12))),
          PExpCompositeVal(PIntLit(42))
        )
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not let a composite (sequence) literal with a key be well-defined if the key is out-of-bounds") {
    val expr = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(
          Some(PExpCompositeVal(PIntLit(12))),
          PExpCompositeVal(PIntLit(42))
        )
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not let a composite (sequence) literal with a key be well-defined if the key is not constant") {
    val args = Vector(PNamedParameter(PIdnDef("n"), PIntType(), false))

    val expr = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(
          Some(PExpCompositeVal(PNamedOperand(PIdnUse("n")))),
          PExpCompositeVal(PIntLit(42))
        )
      ))
    )

    assert (!frontend.wellDefExpr(expr)(args).valid)
  }

  test("TypeChecker: should not type check a composite (sequence) literal with duplicate keys (1)") {
    val expr = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(
          Some(PExpCompositeVal(PIntLit(1))),
          PExpCompositeVal(PIntLit(12))
        ),
        PKeyedElement(
          Some(PExpCompositeVal(PIntLit(1))),
          PExpCompositeVal(PIntLit(24))
        )
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a composite (sequence) literal with duplicate keys (2)") {
    val expr = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(
          None,
          PExpCompositeVal(PIntLit(12))
        ),
        PKeyedElement(
          Some(PExpCompositeVal(PIntLit(0))),
          PExpCompositeVal(PIntLit(24))
        )
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a singleton composite (sequence) literal with an element not matching the sequence type") {
    val expr = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PBoolLit(false)))
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: is able to type check a very simple nested composite (sequence) literal") {
    val expr = PCompositeLit(
      PSequenceType(PSequenceType(PIntType())),
      PLiteralValue(Vector(
        PKeyedElement(None, PLitCompositeVal(PLiteralValue(Vector(
          PKeyedElement(None, PExpCompositeVal(PIntLit(42)))
        ))))
      ))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a nested (sequence) composite singleton literal if the type of the literal value does not match the outer type (1)") {
    val expr = PCompositeLit(
      PSequenceType(PSequenceType(PIntType())),
      PLiteralValue(Vector(
        PKeyedElement(None, PLitCompositeVal(PLiteralValue(Vector(
          PKeyedElement(None, PExpCompositeVal(PBoolLit(true)))
        ))))
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not type check a nested (sequence) composite singleton literal if the type of the literal value does not match the outer type (2)") {
    val expr = PCompositeLit(
      PSequenceType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PLitCompositeVal(PLiteralValue(Vector(
          PKeyedElement(None, PExpCompositeVal(PIntLit(42)))
        ))))
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should assign the correct type to a simple nested composite (sequence) literal") {
    val expr = PCompositeLit(
      PSequenceType(PSequenceType(PIntType())),
      PLiteralValue(Vector(
        PKeyedElement(None, PLitCompositeVal(PLiteralValue(Vector(
          PKeyedElement(None, PExpCompositeVal(PIntLit(42)))
        ))))
      ))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.SequenceT(Type.SequenceT(Type.IntT)) =>
    }
  }

  test("TypeChecker: should let an empty composite integer set literal be well-defined") {
    val expr = PCompositeLit(
      PSetType(PIntType()),
      PLiteralValue(Vector())
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should let a simple singleton composite Boolean set literal be well-defined") {
    val expr = PCompositeLit(
      PSetType(PBoolType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PBoolLit(false)))
      ))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not let a composite set literal be well-defined if the elements do not correctly match the outer type (1)") {
    val expr = PCompositeLit(
      PSetType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PBoolLit(false)))
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not let a composite set literal be well-defined if the elements do not correctly match the outer type (2)") {
    val expr = PCompositeLit(
      PSetType(PSetType(PIntType())),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(42)))
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should assign the correct type to a composite (singleton) set literal (1)") {
    val expr = PCompositeLit(
      PSetType(PBoolType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PBoolLit(false)))
      ))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.SetT(Type.BooleanT) =>
    }
  }

  test("TypeChecker: should assign the correct type to a composite (singleton) set literal (2)") {
    val expr = PCompositeLit(
      PSetType(PSetType(PIntType())),
      PLiteralValue(Vector(
        PKeyedElement(None, PLitCompositeVal(PLiteralValue(Vector(
          PKeyedElement(None, PExpCompositeVal(PIntLit(42)))
        ))))
      ))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.SetT(Type.SetT(Type.IntT)) =>
    }
  }

  test("TypeChecker: should not allow composite set literals to contain keyed elements (1)") {
    val expr = PCompositeLit(
      PSetType(PBoolType()),
      PLiteralValue(Vector(
        PKeyedElement(
          Some(PExpCompositeVal(PIntLit(0))),
          PExpCompositeVal(PBoolLit(true))
        )
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not allow composite set literals to contain keyed elements (2)") {
    val expr = PCompositeLit(
      PSetType(PSetType(PIntType())),
      PLiteralValue(Vector(
        PKeyedElement(None, PLitCompositeVal(PLiteralValue(Vector(
          PKeyedElement(
            Some(PExpCompositeVal(PIntLit(0))),
            PExpCompositeVal(PIntLit(42))
          )
        ))))
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should let an empty composite integer multiset literal be well-defined") {
    val expr = PCompositeLit(
      PMultisetType(PIntType()),
      PLiteralValue(Vector())
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should let a simple singleton composite Boolean multiset literal be well-defined") {
    val expr = PCompositeLit(
      PMultisetType(PBoolType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PBoolLit(false)))
      ))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not let a composite multiset literal be well-defined if the elements do not correctly match the outer type (1)") {
    val expr = PCompositeLit(
      PMultisetType(PIntType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PBoolLit(false)))
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not let a composite multiset literal be well-defined if the elements do not correctly match the outer type (2)") {
    val expr = PCompositeLit(
      PMultisetType(PMultisetType(PIntType())),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PIntLit(42)))
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should assign the correct type to a composite (singleton) multiset literal (1)") {
    val expr = PCompositeLit(
      PMultisetType(PBoolType()),
      PLiteralValue(Vector(
        PKeyedElement(None, PExpCompositeVal(PBoolLit(false)))
      ))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.BooleanT) =>
    }
  }

  test("TypeChecker: should assign the correct type to a composite (singleton) multiset literal (2)") {
    val expr = PCompositeLit(
      PMultisetType(PMultisetType(PIntType())),
      PLiteralValue(Vector(
        PKeyedElement(None, PLitCompositeVal(PLiteralValue(Vector(
          PKeyedElement(None, PExpCompositeVal(PIntLit(42)))
        ))))
      ))
    )

    frontend.exprType(expr)() should matchPattern {
      case Type.MultisetT(Type.MultisetT(Type.IntT)) =>
    }
  }

  test("TypeChecker: should not allow composite multiset literals to contain keyed elements (1)") {
    val expr = PCompositeLit(
      PMultisetType(PBoolType()),
      PLiteralValue(Vector(
        PKeyedElement(
          Some(PExpCompositeVal(PIntLit(0))),
          PExpCompositeVal(PBoolLit(true))
        )
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should not allow composite multiset literals to contain keyed elements (2)") {
    val expr = PCompositeLit(
      PMultisetType(PMultisetType(PIntType())),
      PLiteralValue(Vector(
        PKeyedElement(None, PLitCompositeVal(PLiteralValue(Vector(
          PKeyedElement(
            Some(PExpCompositeVal(PIntLit(0))),
            PExpCompositeVal(PIntLit(42))
          )
        ))))
      ))
    )

    assert (!frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type a nesting of set and sequence composite literals in combination with the use of keys") {
    val expr = PCompositeLit(
      PSetType(PSequenceType(PIntType())),
      PLiteralValue(Vector(
        PKeyedElement(None, PLitCompositeVal(PLiteralValue(Vector(
          PKeyedElement(Some(PExpCompositeVal(PIntLit(1))), PExpCompositeVal(PIntLit(42))),
          PKeyedElement(Some(PExpCompositeVal(PIntLit(0))), PExpCompositeVal(PIntLit(12)))
        ))))
      ))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }

  test("TypeChecker: should correctly type a nesting of multiset and sequence composite literals in combination with the use of keys") {
    val expr = PCompositeLit(
      PMultisetType(PSequenceType(PIntType())),
      PLiteralValue(Vector(
        PKeyedElement(None, PLitCompositeVal(PLiteralValue(Vector(
          PKeyedElement(Some(PExpCompositeVal(PIntLit(1))), PExpCompositeVal(PIntLit(42))),
          PKeyedElement(Some(PExpCompositeVal(PIntLit(0))), PExpCompositeVal(PIntLit(12)))
        ))))
      ))
    )

    assert (frontend.wellDefExpr(expr)().valid)
  }


  /* * Stubs, mocks, and other test setup  */

  class TestFrontend {
    def stubProgram(inArgs: Vector[PParameter], body : PStatement) : PProgram = PProgram(
      PPackageClause(PPkgDef("pkg")),
      Vector(),
      Vector(PMethodDecl(
        PIdnDef("foo"),
        PUnnamedReceiver(PMethodReceiveName(PNamedOperand(PIdnUse("self")))),
        inArgs,
        PResult(Vector()),
        PFunctionSpec(Vector(), Vector(), true),
        Some(PBlock(Vector(body)))
      ))
    )

    def singleExprProgram(inArgs: Vector[PParameter], expr : PExpression) : PProgram = {
      val stmt = PShortVarDecl(Vector(expr), Vector(PIdnUnk("n")), Vector(false))
      stubProgram(inArgs, stmt)
    }

    def singleExprTypeInfo(inArgs: Vector[PParameter], expr : PExpression) : TypeInfoImpl = {
      val program = singleExprProgram(inArgs, expr)
      val pkg = PPackage(
        PPackageClause(PPkgDef("pkg")),
        Vector(program),
        new PositionManager()
      )
      val tree = new Info.GoTree(pkg)
      val context = new Info.Context()
      val config = Config(Vector(), Vector())
      new TypeInfoImpl(tree, context)(config)
    }

    def exprType(expr : PExpression)(inArgs: Vector[PParameter] = Vector()) : Type.Type =
      singleExprTypeInfo(inArgs, expr).exprType(expr)

    def isGhostExpr(expr : PExpression)(inArgs: Vector[PParameter] = Vector()) : Boolean =
      singleExprTypeInfo(inArgs, expr).isExprGhost(expr)

    def isPureExpr(expr : PExpression)(inArgs: Vector[PParameter] = Vector()) : Boolean =
      singleExprTypeInfo(inArgs, expr).isPureExpr(expr).isEmpty

    def wellDefExpr(expr : PExpression)(inArgs: Vector[PParameter] = Vector()) =
      singleExprTypeInfo(inArgs, expr).wellDefExpr(expr)
  }
}