# Doop 101

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
(right-arrow notation) are in [path.logic](doop-101-examples/path.logic).

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
[path-facts.logic](doop-101-examples/path-facts.logic). The `+` operator in
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
$ bloxbatch -db DB -create -overwrite -block base                          # create our database
$ bloxbatch -db DB -addBlock -file docs/doop-101-examples/path.logic       # load our rules
$ bloxbatch -db DB -execute -file docs/doop-101-examples/path-facts.logic  # load our facts
$ bloxbatch -db DB -print Path                                             # print computed results
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

## Entities
The analogous to user-defined types in Datalog are **Entity** predicates. Those
are unary predicates and internally the engine will use some unique ID for each
entry of the same Entity predicate. There are two ways to create a new entity
in Datalog--refmode predicates and constructor predicates.

### Refmode predicates
You can think of **refmode** predicates as a mapping from a primitive type to an
entity. An example of refmode predicates is found in
[ancestors.logic](doop-101-examples/ancestors.logic) and
[ancestors-facts.logic](doop-101-examples/ancestors-facts.logic).

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
$ bloxbatch -db DB -create -overwrite -block base
$ bloxbatch -db DB -addBlock -file docs/doop-101-examples/ancestors.logic
$ bloxbatch -db DB -execute -file docs/doop-101-examples/ancestors-facts.logic
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
each different combination to a unique entity. An example of constructor
predicates is found in [person.logic](doop-101-examples/person.logic) and
[person-facts.logic](doop-101-examples/person-facts.logic).

```
#!java
```
// Declarations
Person(x) -> .
Person:cons[name, age] = p -> string(name), int[32](age), Person(p).

lang:physical:storageModel[`Person] = "ScalableSparse".
lang:entity(`Person).
lang:constructor(`Person:cons).
```
#!java
```
+Person(p), +Person:cons[name, age] = p <-
  name = "dave", age = 70.
+Person(p), +Person:cons[name, age] = p <-
  name = "john", age = 50.
+Person(p), +Person:cons[name, age] = p <-
  name = "harry", age = 25.
```
#!bash
$ bloxbatch -db DB -create -overwrite -block base
$ bloxbatch -db DB -addBlock -file docs/doop-101-examples/person.logic
$ bloxbatch -db DB -execute -file docs/doop-101-examples/person-facts.logic
$ bloxbatch -db DB -print Person
predicate: Person(uint[32])
...
/--- start of Person facts ---\
  [0]*0
  [1]*1
  [2]*2
\---- end of Person facts ----/
```

In this example, `Person:cons` is a constructor predicate that constructs a new
entity for each pair of name and age.  The lines starting with `lang:` are
directives to the Datalog engine and are necessary when dealing with
constructors.  You can think of a refmode predicate as a constructor with a
single key argument.

---

## Running an analysis

Now let's focus on more meaningful examples using Doop directly. We will use a
running example, found in [Example.java](doop-101-examples/Example.java).

We will run a simple naive analysis (`-a naive` option) on the generated jar
file (`-j Example.jar` option). This analysis has only a few basic rules but
it's a good representative skeleton of actual analyses. Since Doop performs a
whole program analysis, the library will be analyzed along with application
code. We can also specify the desired JRE version of library code (`--jre 1.7`
option).

```
#!bash
$ ./doop -a naive -j docs/doop-101-examples/Example.jar --jre 1.7 --Xstats:none
```

After the analysis has run, we can gather results by issuing queries directly to the database.

## Analysis structure
Input facts are auto-generated by the framework (using
[Soot](https://github.com/Sable/soot)) and then imported to the database so our
rules can process them. The input schema can be found

* in `logic/facts/declarations.logic`
* and in `logic/facts/flow-insensitivity-declarations.logic`.

The rules for our naive analysis can be found in `logic/analyses/naive/analysis.logic`.

For example, the following rule states that **when** we have a heap allocation
of a `?heap` object that is assigned to variable `?var` inside a method deemed
reachable by our analysis, **then** we can infer that the variable may point to
this heap object.

```
#!java
VarPointsTo(?var, ?heap) <-
  AssignHeapAllocation(?heap, ?var, ?inMethod),
  Reachable(?inMethod).
```

Furthermore, **when** we have some kind of assignment (direct or indirect) from
one variable to another, and we know that the source variable may point to some
heap object, **then** the target variable may point to the same heap object.

```
#!java
VarPointsTo(?to, ?heap) <-
  Assign(?to, ?from),
  VarPointsTo(?from, ?heap).
```

Notice here that this rule is **recursive**; previously known facts about the
`VarPointTo` relation may lead to the inference of additional facts. Doop
analysis rules are **mutually recursive** in complex ways.

## Accessing the database
After the end of an analysis, a symbolic link for the resulting database can be
found under the `results` directory. Also, for convenience, a second symbolic
link is created at top level called `last-analysis`, each time pointing to the
latest successful analysis.

### Get a predicate's entries
As we already saw, the easiest way to interact with the database is to simply
print all the entries of a certain predicate.

```
#!bash
$  bloxbatch -db last-analysis -print FieldPointsTo
predicate: FieldPointsTo(HeapAllocation, FieldSignature, HeapAllocation)
...
/--- start of FieldPointsTo facts ---\
  [113914]Example.test/new Cat/1, [11417]<Cat: Cat parent>, [113915]Example.test/new Cat/2
\---- end of FieldPointsTo facts ----/
```

Doop represents (and abstracts away) objects by using their allocation point in
the program. `Example.test/new Cat/1` refers to the second (zero-based
indexing) Cat object allocated inside method `Example.test()`.

As expected, the `parent` field of the second Cat object may point to the third Cat object.


### Query 1
Our first query is to ask for `VarPointTo` entries of variables declared in `Example.morePlay()`.

```
#!bash
$ bloxbatch -db last-analysis -query \
'_(?var, ?heap) <- VarPointsTo(?var, ?heap), Var:DeclaringMethod(?var, "<Example: void morePlay(Cat)>").'
  Example.morePlay/@this, Example.main/new Example/0
  Example.morePlay/r0, Example.main/new Example/0
  Example.morePlay/r3, Example.test/new Cat/1
  Example.morePlay/r4, Example.test/new Cat/1
  Example.morePlay/r1, Example.test/new Cat/1
  Example.morePlay/@param0, Example.test/new Cat/1
  Example.morePlay/r2, Example.test/new Cat/2
  Example.morePlay/r3, Example.test/new Cat/2
  Example.morePlay/r4, Example.test/new Cat/2
```

The string provided to the `-query` option can be a set of left and right arrow
Datalog rules. Newly defined predicates have to start with `_` since they will
only exist for the duration of the query evaluation. Refmode values can be used
directly, and the engine will automatically substitute them with their internal
IDs. E.g., the following part

```
#!java
Var:DeclaringMethod(?var, "<Example: void morePlay(Cat)>")
```

is equivalent to

```
#!java
Var:DeclaringMethod(?var, ?method), MethodSignature:Value(?method:"<Example: void morePlay(Cat)>")
```

Note here that Doop analyzes Java **bytecode**. Input facts are generated using
Soot, which transforms Java bytecode to
[Jimple](https://en.wikipedia.org/wiki/Soot_%28software%29#Jimple), a language
based on *three address code*. As a result new temporary variables are
introduced and also original variable names might be lost (they can be retained
through specific flags in javac and Soot).

### Query 2
A more advanced query can be found in
[query2.logic](doop-101-examples/query2.logic). Essentially, we compute a
transitive closure on the `CallGraphEdge` predicate. The logic used in a query
can be as complicated as in any "normal" Datalog program.

```
#!bash
_path(?fromMethod, ?toMethod) <-
  CallGraphEdge(?invocation, ?toMethod),
  Instruction:Method[?invocation] = ?fromMethod.

_path(?fromMethod, ?toMethod) <-
  _path(?fromMethod, ?toMethodMid),
  CallGraphEdge(?invocation, ?toMethod),
  Instruction:Method[?invocation] = ?toMethodMid.
```

```
#!bash
$ bloxbatch -db last-analysis -query -file docs/doop-101-examples/query2.logic
  <Example: void main(java.lang.String[])>, <Example: void test(int)>
  <Example: void test(int)>, <Example: void morePlay(Cat)>
  <Example: void main(java.lang.String[])>, <Example: void morePlay(Cat)>
  <Example: void test(int)>, <Cat: void setParent(Cat)>
  <Example: void main(java.lang.String[])>, <Cat: void setParent(Cat)>
  <Example: void test(int)>, <Cat: Cat getParent()>
  <Example: void morePlay(Cat)>, <Cat: Cat getParent()>
  <Example: void main(java.lang.String[])>, <Cat: Cat getParent()>
  <Example: void test(int)>, <Cat: void play()>
  <Example: void morePlay(Cat)>, <Cat: void play()>
  <Animal: Animal playWith(Animal)>, <Cat: void play()>
  <Example: void main(java.lang.String[])>, <Cat: void play()>
  <Example: void test(int)>, <Dog: void play()>
  <Example: void main(java.lang.String[])>, <Dog: void play()>
  <Example: void test(int)>, <Animal: Animal playWith(Animal)>
  <Example: void morePlay(Cat)>, <Animal: Animal playWith(Animal)>
  <Example: void main(java.lang.String[])>, <Animal: Animal playWith(Animal)
```

The line `Instruction:Method[?invocation] = ?fromMethod` found in the previous
query uses a special form of predicate known as a **functional** predicate.
Those are similar to normal ones, but they act like a map. Values found between
the square brackets are mapped to only on value on the right.

### Doop & the DaCapo Benchmarks suite
We frequently analyze various programs from the [DaCapo Benchmarks
suite](http://www.dacapobench.org/) using a variety of advanced analyses. E.g.,
let's analyze the `antlr` benchmark using a 2 type-sensitive analysis.

```
#!bash
$ ./doop -a 2-type-sensitive+heap -j benchmarks/dacapo-2006/antlr.jar --dacapo --jre 1.7
```

Towards the end of execution, Doop will report a set of metrics gathered from
the analyzed program. Those metrics are computed through the use of various
queries on the resulting database. Those can be found under
`logic/addons/statistics`.

### Query 3
For example, one metric is the computation of casts that potentially may fail.
It joins input facts as well as facts computed during execution to infer casts
where the related variable may point to an object that is incompatible with the
type of the cast.

```
#!java
_Stats:Simple:PotentiallyFailingCast(?type, ?from, ?to) <-
    _Stats:Simple:ReachableCast(_, ?type, ?to, ?from),
    Stats:Simple:InsensVarPointsTo(?heap, ?from),
    HeapAllocation:Type[?heap] = ?heaptype,
    ! AssignCompatible(?type, ?heaptype).
```

The use of `_` as a predicate parameter denotes that we don't care for a
specific value. It represent a parameter that is not bound.

The above query can be found isolated in [query3.logic](doop-101-examples/query3.logic).

```
#!bash
$ bloxbatch -db last-analysis -query -file docs/doop-101-examples/query3.logic
```

### Aggregate Functions
Datalog supports the use of aggregation functions. One such function is
`count`. E.g., let's to compute the total number of VarPointsTo entries.

```
#!bash
$ bloxbatch -db last-analysis -query \
'_[] = ?n <- agg<<?n = count()>> VarPointsTo(_, _, _, _).'
  4569312
```

***

## Auxiliary Tools

### mkjar
You can use `bin/mkjar` to easily generate a jar file from a single java file.
The generated jar file will contain local variable debugging information (e.g.,
variable names).

Example:
```
#!bash
$ ./bin/mkjar Example.java
added manifest
adding: Dog.class(in = 292) (out= 210)(deflated 28%)
adding: Animal.class(in = 434) (out= 280)(deflated 35%)
adding: Cat.class(in = 505) (out= 296)(deflated 41%)
adding: Example.class(in = 1055) (out= 653)(deflated 38%)
```

### bytecode2jimple
You can use `bytecode2jimple` to easily generate Jimple (or Shimple--the ssa
variant) from a jar file. For more information, invoke bytecode2jimple without
arguments.

Example:
```
#!bash
$ cd bytecode2jimple
$ ./run -lsystem -d jimple-dir ../Example.jar
$ ls jimple-dir
Animal.jimple  Cat.jimple  Dog.jimple  Example.jimple
```
