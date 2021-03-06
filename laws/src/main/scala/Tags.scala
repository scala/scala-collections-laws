package laws

import scala.language.implicitConversions

/** Tags provide a way to select which laws are applicable for a given run.  For instance,
  * if you are testing collections with `Int`s and with `String`s, some tests may be
  * specific to the collection type; in that case you would tag the appropriate laws
  * using a string that helps you distinguish them. (E.g. `Tags("Int")`.)
  *
  * Additional restriction of the set may be achieved by including tests in `select`.  All
  * such tests must pass for a particular set of parameters to be included in a test.
  */
case class Tags(positive: Set[Flag], negative: Set[Flag], select: Vector[TestInfo => Option[Outcome.Skip]]) {
  /** Tests whether any tags are present (either boolean or string-valued) */
  val isEmpty = positive.isEmpty && negative.isEmpty && select.isEmpty

  /** Checks whether a certain set of flags is compatible for code generation (compile-time compatible) */
  def compatible(flags: Set[Flag]) = 
    positive.forall(f => f.disabled || flags.contains(f)) && !flags.exists(f => !f.disabled && negative.contains(f))

  /** Checks whether a certain choice of parameters is suitable for testing at runtime */
  def validate(t: TestInfo): Option[Outcome.Skip] = select.iterator.map(p => p(t)).find(_.isDefined).flatten

  /** Sets a boolean tag that must be present */
  def need(t: Flag): Tags =
    if (positive contains t) this
    else if (negative contains t) new Tags(positive + t, negative - t, select)
    else new Tags(positive + t, negative, select)

  /** Requires that a particular tag be absent */
  def shun(t: Flag): Tags =
    if (negative contains t) this
    else if (positive contains t) new Tags(positive - t, negative + t, select)
    else new Tags(positive, negative + t, select)

  /** Adds an extra selector that checks test info */
  def filter(p: TestInfo => Option[Outcome.Skip]): Tags = new Tags(positive, negative, select :+ p)

  override lazy val toString = {
    val named = positive.toList.sorted ::: negative.toList.map("!" + _).sorted
    val pred  = select.length match {
      case 0 => ""
      case 1 => "(1 filter)"
      case n => f"($n filters)"
    }
    (if (pred.nonEmpty) named.toVector :+ pred else named.toVector).mkString(" ")
  }
}
object Tags {
  /** Taggish represents values that can be tags: either a key alone, or a key-value pair (all strings). */
  sealed trait Taggish {}
  final case class PosTag(flag: Flag) extends Taggish {}
  final case class NegTag(flag: Flag) extends Taggish {}
  final case class SelectTag(p: TestInfo => Option[Outcome.Skip]) extends Taggish {}
  //final case class SelectTag(p: List[String] => Boolean) extends Taggish {}
  /** Implicits contains implicit conversions to values that can be tags. */
  object Implicits {
    implicit class TagIsTaggish(flag: Flag) {
      def y: PosTag  = PosTag(flag)
      def n: NegTag  = NegTag(flag)
      def ! : NegTag = NegTag(flag)
    }
    implicit def flagIsPositiveByDefault(flag: Flag): PosTag = PosTag(flag)
    implicit def selectWrapsFunction(p: TestInfo => Option[Outcome.Skip]): SelectTag = SelectTag(p)
  }

  /** Canonical empty set of tags. (That is, no tags.) */
  val empty = new Tags(Set.empty[Flag], Set.empty[Flag], Vector.empty[TestInfo => Option[Outcome.Skip]])

  /** Create a mixed set of boolean and predicate tags.
    *
    * First, `import laws.Tags.Implicits._`.  Then use `"seq".y, "set".n, select(_.hasZero)` to set,
    * in this example, a tag that must be present, mustn't be present, and a test that must pass, respectively.
    *
    * Note: this is the best place to alter the code to ignore CAMEL tags (used to suppress errors with strawman collections)
    */
  def apply(key: Taggish, keys: Taggish*) = {
    val all = key :: keys.toList
    val positive = all.collect{ case PosTag(s)    => s }.toSet
    new Tags(
      positive,
      all.collect{ case NegTag(s) /* if !s.isCamel */ => s }.toSet &~ positive,  // Comment in/out !s.isCamel to suppress known strawman errors
      all.collect{ case SelectTag(p) => p }.toVector
    )
  }
}
