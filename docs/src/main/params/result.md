# Result

Components use **Results** to return results in the form of a **ParameterSet**. `Result` is the value that is returned
as an argument to the `CompletedWithResult` `SubmitResponse`. 

Creating a Result Requires:

 * **[Prefix](commands.html#Prefix)**
 * **[Set[Parameter]](keys-parameters.html)**

Scala
:   @@snip [ResultTest.scala](../../../../examples/src/test/scala/example/params/ResultTest.scala) { #result }

Java
:   @@snip [JResultTest.java](../../../../examples/src/test/java/example/params/JResultTest.java) { #result }

## JSON serialization
State variables can be serialized to JSON. The library has provided **JsonSupport** helper class and methods to serialize DemandState and CurrentState.

Scala
:   @@snip [ResultTest.scala](../../../../examples/src/test/scala/example/params/ResultTest.scala) { #json-serialization }

Java
:   @@snip [JResultTest.java](../../../../examples/src/test/java/example/params/JResultTest.java) { #json-serialization }

## Unique Key Constraint

By choice, a ParameterSet in **Result** will be optimized to store only unique keys. In other words, trying to store multiple keys with same name, will be automatically optimized by removing duplicates.

@@@ note

Parameters are stored in a Set, which is an unordered collection of items. Hence, it's not predictable whether first or last duplicate copy will be retained. Hence, cautiously avoid adding duplicate keys.

@@@    

Here are some examples that illustrate this point:

Scala
:   @@snip [ResultTest.scala](../../../../examples/src/test/scala/example/params/ResultTest.scala) { #unique-key }

Java
:   @@snip [JResultTest.java](../../../../examples/src/test/java/example/params/JResultTest.java) { #unique-key }

## Source Code for Examples

* @github[Scala Example](/examples/src/test/scala/example/params/ResultTest.scala)
* @github[Java Example](/examples/src/test/java/example/params/JResultTest.java)