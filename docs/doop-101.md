# Doop 101

The building blocks of Datalog programs come in the form of **predicates**. Our input facts (a.k.a. EDB logic) are represented as predicate values.

Example:
```
#!java
Person("John").
Parent("John", "Johnny jr").
```

Then we have **rules** (a.k.a. IDB logic) to infer new information from facts already known to be true. This continues until no new information can be extracted.

Example:
```
#!java
Ancestor(x, y) <- Parent(x, y).
```

To familiarize yourself with Datalog evaluation, you may want to try the [LogicBlox REPL tutorial](https://developer.logicblox.com/content/docs4/tutorial/repl/section/split.html).


## Toy Example

We want to compute the ancestors for a set of people. Our rules (left-arrow notation) along with their type declarations (right-arrow notation) are in `ancestors.logic`.


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

Our input facts (a.k.a. delta logic) are in `facts.logic`. The `+` operator in front of the predicates (called **delta** predicates) denotes that we want to add facts to our database (we could also remove or update facts).

```
#!java

+Person(x), +Name(x:"dave").
+Person(x), +Name(x:"john").
+Person(x), +Name(x:"harry").

+Parent(x,y) <- Name(x:"dave"), Name(y:"john").
+Parent(x,y) <- Name(x:"john"), Name(y:"harry").
```

We invoke actions on our Datalog engine using `bloxbatch`.

```
#!bash

$ bloxbatch -db DB -create -overwrite              # create our database
$ bloxbatch -db DB -addBlock -file ancestors.logic # load our rules
$ bloxbatch -db DB -execute -file facts.logic      # load our facts
$ bloxbatch -db DB -print Ancestor                 # print computed results
predicate: Ancestor(Person, Person)
...
/--- start of Ancestor facts ---\
  [1]john, [0]harry
  [2]dave, [0]harry
  [2]dave, [1]john
\---- end of Ancestor facts ----/
```

### Refmode

The line `Person(x), Name(x:n) -> string(n).` states that a person (represented by an internal ID) can be constructed by providing a string in the `Name` predicate. This is known as *refmode*. This is also shown in the print output. For example, `[1]john` means that the string "john" is used to create a Person with the ID 1.
