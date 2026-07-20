import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// A tag lets you select/deselect tests. Exclude tagged tests with:
//   sbt "testOnly * -- -l Unnecessary"
object Unnecessary extends Tag("Unnecessary")

class TagTest extends AnyFlatSpec with Matchers {
  "Integers" should "add" taggedAs (Unnecessary) in {
    17 + 25 should be(42)
  }
}
