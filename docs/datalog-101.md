# Datalog 101

The building blocks of Datalog programs come in the form of **predicates**. Our
input facts (a.k.a. EDB logic) are represented as predicate values.

Example:
```
#!java
Edge(1, 2).
Person("john").
```

Then we have **rules** (a.k.a. IDB logic) to infer new information from facts
already known to be true. This continues until no new information can be
extracted (fixed-point computation).

Example:
```
#!java
Path(x, y) <- Edge(x, y).
```

To familiarize yourself with Datalog, you may want to try writing your own
examples using the [LogicBlox REPL
tutorial](https://developer.logicblox.com/content/docs4/tutorial/repl/section/split.html).
An alternative source of tutorials is the official [LogicBlox
blog page](https://developer.logicblox.com/category/tutorials/basics/).

## Toy Example

We want to compute existing paths in a graph, which is described by a set of
edges. Our rules (left-arrow notation) along with their type declarations
(right-arrow notation) are in [path.logic](datalog-101-examples/path.logic).

```
#!java
// Declarations
Edge(x, y) -> int[32](x), int[32](y).
Path(x, y) -> int[32](x), int[32](y).

// Rules
Path(x, y) <- Edge(x, y).
Path(x, y) <- Path(x, z), Path(z, y).
```

Our input facts (a.k.a. delta logic) are in
[path-facts.logic](datalog-101-examples/path-facts.logic). The `+` operator in
front of predicates (called **delta** predicates) denotes that we want to add
facts to our database (we could also remove or update facts).

```
#!java
+Edge(1, 2).
+Edge(2, 3).
+Edge(3, 4).
+Edge(3, 5).
+Edge(4, 6).
```

We invoke actions on our Datalog engine using `bloxbatch`.

```
#!bash
$ bloxbatch -db DB -create -overwrite -blocks base                            # create our database
$ bloxbatch -db DB -addBlock -file docs/datalog-101-examples/path.logic       # load our rules
$ bloxbatch -db DB -execute -file docs/datalog-101-examples/path-facts.logic  # load our facts
$ bloxbatch -db DB -print Path                                                # print computed results
predicate: Path(int[32], int[32])
  label: Path
  storage model: Sparse
  derivation type: DerivedAndStored
  default value: null
  size: 13
  type map: false
  partitioning: master only
  locking: ByPredicate
/--- start of Path facts ---\
  1, 2
  1, 3
  1, 4
  1, 5
  1, 6
  2, 3
  2, 4
  2, 5
  2, 6
  3, 4
  3, 5
  3, 6
  4, 6
\---- end of Path facts ----/
```
---

## Entities
The analogous to user-defined types in Datalog are **Entity** predicates. Those
are unary predicates and internally the engine will use some unique ID for each
entry of the same Entity predicate. There are two ways to create new entities
in Datalog--refmode predicates and constructor predicates.

### Refmode predicates
You can think of **refmode** predicates as a mapping from a primitive type to an
entity. An example of refmode predicates is found in
[ancestors.logic](datalog-101-examples/ancestors.logic) and
[ancestors-facts.logic](datalog-101-examples/ancestors-facts.logic).

```
#!java
// Declarations
Person(x), Name(x:n) -> string(n).
Parent(x,y) -> Person(x), Person(y).
Ancestor(x,y) -> Person(x), Person(y).

// Rules
Ancestor(x,y) <- Parent(x,y).
Ancestor(x,y) <- Ancestor(x,z), Ancestor(z,y).
```

```
#!java
+Person(x), +Name(x:"dave").
+Person(x), +Name(x:"john").
+Person(x), +Name(x:"harry").

+Parent(x,y) <- Name(x:"dave"), Name(y:"john").
+Parent(x,y) <- Name(x:"john"), Name(y:"harry").
```

```
#!bash
$ bloxbatch -db DB -create -overwrite -blocks base
$ bloxbatch -db DB -addBlock -file docs/datalog-101-examples/ancestors.logic
$ bloxbatch -db DB -execute -file docs/datalog-101-examples/ancestors-facts.logic
$ bloxbatch -db DB -print Ancestor
predicate: Ancestor(Person, Person)
...
/--- start of Ancestor facts ---\
  [1]john, [0]harry
  [2]dave, [0]harry
  [2]dave, [1]john
\---- end of Ancestor facts ----/
```

Here `Person` is an entity predicate and `Name` is a refmode predicate. In
`Name(x:n)`, `x` is bound to the internal ID the engine gave to the new entity,
and `n` is the primitive value mapped to this new entity. In the print output,
`[1]john` means that the string "john" is used to create a Person entity with
ID 1.

### Constructor predicates
You can think of **constructor** predicates as a more complex version of
refmode predicates. A constructor takes a number of **key** arguments, and maps
each combination to an entity. An example of constructor predicates is
found in [person.logic](datalog-101-examples/person.logic) and
[person-facts.logic](datalog-101-examples/person-facts.logic).

```
#!java
// Declarations
Person(x) -> .
Person:cons[name, age] = p -> string(name), int[32](age), Person(p).

lang:physical:storageModel[`Person] = "ScalableSparse".
lang:entity(`Person).
lang:constructor(`Person:cons).
```

```
#!java
+Person(p), +Person:cons[name, age] = p <-
  name = "dave", age = 70.
+Person(p), +Person:cons[name, age] = p <-
  name = "john", age = 50.
+Person(p), +Person:cons[name, age] = p <-
  name = "harry", age = 25.
```

```
#!bash
$ bloxbatch -db DB -create -overwrite -blocks base
$ bloxbatch -db DB -addBlock -file docs/datalog-101-examples/person.logic
$ bloxbatch -db DB -execute -file docs/datalog-101-examples/person-facts.logic
$ bloxbatch -db DB -print Person
predicate: Person(uint[32])
...
/--- start of Person facts ---\
  [0]*0
  [1]*1
  [2]*2
\---- end of Person facts ----/
$ bloxbatch -db DB -print Person:cons
predicate: Person:cons[string, int[32]] = Person
...
/--- start of Person:cons facts ---\
  dave, 70, [2]*2
  harry, 25, [0]*0
  john, 50, [1]*1
\---- end of Person:cons facts ----/
```

In this example, `Person:cons` is a constructor predicate that constructs a new
entity from a name and age pair. Although constructors are more powerful than
refmodes, you can think of a refmode predicate as a constructor with a single
key argument. The lines starting with `lang:` are directives to the Datalog
engine and are necessary when dealing with constructors. As a sidenote, you can
think of `:` as part of a predicate's name, with no significant meaning.

---

## Exercise

Assume a `Next(i, j)` relation on code instructions (instruction `j` is after instruction `i`) and implement:

* `Reachable(i,j)`
* `ReachableBypassing(i,j,k)`
* `ReachableFromEntry(i)`, assuming an `EntryInstruction(j)` relation
* `CanReachReturn(i)`, assuming a `ReturnInstruction(j)` relation

How about:

* `CanReachAllReturns(i)`
* `AllPredecessorsReachableFromEntry(i)`

A possible implementation is found in [graph.logic](datalog-101-examples/graph.logic).
