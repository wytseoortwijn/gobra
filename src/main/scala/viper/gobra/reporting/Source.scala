// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.
//
// Copyright (c) 2011-2020 ETH Zurich.

package viper.gobra.reporting

import viper.silver.ast.SourcePosition
import viper.silver.{ast => vpr}
import viper.gobra.ast.{frontend, internal}
import viper.gobra.util.Violation
import viper.silver.ast.utility.rewriter.{SimpleContext, Strategy, StrategyBuilder, Traverse}
import viper.silver.ast.utility.rewriter.Traverse.Traverse

import scala.annotation.tailrec

object Source {

  sealed abstract class AbstractOrigin(val pos: SourcePosition, val tag: String)
  case class Origin(override val pos: SourcePosition, override val tag: String) extends AbstractOrigin(pos, tag)
  case class AnnotatedOrigin(origin: AbstractOrigin, annotation: Annotation) extends AbstractOrigin(origin.pos, origin.tag)
  trait Annotation

  object Parser {

    sealed trait Info {
      def origin: Option[AbstractOrigin]
      def vprMeta(node: internal.Node): (vpr.Position, vpr.Info, vpr.ErrorTrafo)
    }

    object Unsourced extends Info {
      override def origin: Option[AbstractOrigin] = throw new IllegalStateException()
      override def vprMeta(node: internal.Node): (vpr.Position, vpr.Info, vpr.ErrorTrafo) = throw new IllegalStateException()
    }

    case object Internal extends Info {
      override lazy val origin: Option[AbstractOrigin] = None
      override def vprMeta(node: internal.Node): (vpr.Position, vpr.Info, vpr.ErrorTrafo) =
        (vpr.NoPosition, vpr.NoInfo, vpr.NoTrafos)
    }

    case class Single(pnode: frontend.PNode, src: AbstractOrigin) extends Info {
      override lazy val origin: Option[AbstractOrigin] = Some(src)
      override def vprMeta(node: internal.Node): (vpr.Position, vpr.Info, vpr.ErrorTrafo) =
        (vpr.TranslatedPosition(src.pos), Verifier.Info(pnode, node, src), vpr.NoTrafos)

      def createAnnotatedInfo(annotation: Annotation): Single = Single(pnode, AnnotatedOrigin(src, annotation))
    }

    object Single {
      def fromVpr(src: vpr.Exp): Single = {
        val info = Source.unapply(src).get
        Single(info.pnode, info.origin)
      }
    }
  }

  object Verifier {
    case class Info(pnode: frontend.PNode, node: internal.Node, origin: AbstractOrigin, comment: Seq[String] = Vector.empty) extends vpr.Info {
      override def isCached: Boolean = false
      def addComment(cs : Seq[String]) : Info = Info(pnode, node, origin, comment ++ cs)
    }
  }

  object Synthesized {
    def unapply(node : vpr.Node) : Option[Verifier.Info] = node.meta._2 match {
      case vpr.SimpleInfo(comment) => searchInfo(node).map(_.addComment(comment))
      case _ => None
    }
  }

  def unapply(node: vpr.Node): Option[Verifier.Info] = searchInfo(node)

  def withInfo[N <: vpr.Node](n: (vpr.Position, vpr.Info, vpr.ErrorTrafo) => N)(source: internal.Node): N = {
    source.info match {
      case Parser.Internal => n(vpr.NoPosition, vpr.NoInfo, vpr.NoTrafos)
      case Parser.Unsourced => throw new IllegalStateException()

      case Parser.Single(pnode, origin) =>

        val newInfo = Verifier.Info(pnode, source, origin)
        val newPos  = vpr.TranslatedPosition(origin.pos)

        n(newPos, newInfo, vpr.NoTrafos)
    }
  }

  /**
    * Searches for source information  in the AST (sub)graph of `node`
    * and returns the first info encountered; or `None` if no such info exists.
    */
  def searchInfo(node : vpr.Node) : Option[Verifier.Info] = {
    node.getPrettyMetadata._2.getUniqueInfo[Verifier.Info] match {
      case Some(info) => Some(info)
      case _ => searchInfo(node.subnodes)
    }
  }

  @tailrec
  private def searchInfo(nodes : Seq[vpr.Node]) : Option[Verifier.Info] = nodes match {
    case Seq() => None
    case nodes => searchInfo(nodes.head) match {
      case Some(info) => Some(info)
      case None => searchInfo(nodes.tail)
    }
  }


  implicit class RichViperNode[N <: vpr.Node](node: N) {

    def withInfo(source: internal.Node): N = {
      val (pos, info, errT) = node.getPrettyMetadata

      def message(fieldName: String) = {
        s"Node to annotate ('$node' of class ${node.getClass.getSimpleName}) already has " +
          s"field '$fieldName' set"
      }

      require(info == vpr.NoInfo, message("info"))
      require(pos == vpr.NoPosition, message("pos"))

      source.info match {
        case Parser.Internal => node
        case Parser.Unsourced => Violation.violation(s"information cannot be taken from an unsourced node $source")

        case Parser.Single(pnode, origin) =>

          val newInfo = Verifier.Info(pnode, source, origin)
          val newPos  = vpr.TranslatedPosition(origin.pos)

          node.withMeta(newPos, newInfo, errT).asInstanceOf[N]
      }
    }

    def transformWithNoRec(pre: PartialFunction[vpr.Node, vpr.Node] = PartialFunction.empty,
                  recurse: Traverse = Traverse.TopDown)
    : N = {
      var strategy: Strategy[vpr.Node, SimpleContext[vpr.Node]] = null
      strategy = StrategyBuilder.Slim[vpr.Node]({ case n: vpr.Node =>
        if (pre.isDefinedAt(n)) pre(n)
        else strategy.noRec(n)
      }, recurse)
      strategy.execute[N](node)
    }

    def withDeepInfo(source: internal.Node): N = {
      node.transformWithNoRec{
        case n: vpr.Node
          if {val m = n.getPrettyMetadata; m._1 == vpr.NoPosition && m._2 == vpr.NoInfo} =>
          n.withInfo(source)
      }
    }
  }
}
