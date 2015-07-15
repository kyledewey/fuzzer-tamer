# fuzzer-tamer
Tool for organizing failure-producing inputs from a fuzzer.  Ideas behind this are based on Chen et al.'s "Taming Compiler Fuzzers"

This is a parallel fuzzer tamer, which should be extensible to multiple applications.
All you need to provide is:

1. A way to go from raw output to some parsed representation
2. A way to determine if two different parsed representations should be placed into the same equivalence class

The framework provides the rest.

# Usage #

Example usage is as so:

```console
sbt "run-main tamer.Main rust files/ *.output equiv_classes"
```

...where `rust` is the name of the parser used to parse output files, `files/` is a directory containing raw output, `*.output` is the extension for raw output files, and `equiv_classes` is a directory where equivalence class information is placed.
Each bug is placed into a directory named `bugN`, where `N` is a natural number.
Each of these directories contains files which contain the names of original input files as part of the original equivalence class.
For example, we might have a directory structure like the following:

- `bug0`
    - `0`
    - `3`
- `bug1`
    - `2`
- `bug3`
    - `0`
    - `1`
    - `2`

...where the files underneath the `bugN` directories contain input files, one per line.

# On Design #
This multi-file approach was taken on the following assumptions:
- There are many raw output files
- There are many unique bugs

In conjunction, the above two points mean that the majority of time is likely spent in comparing equivalence classes, which should never block.
This technique also is easy on memory; we only need memory for one representative of each equivalence class, as opposed to saving up files and writing at the end.
Collating until the end is unacceptable if we have any significant number of input files.

The reason why the bug equivalence class directory structure is split up is because this allows us to avoid locking when multiple threads find two distinct inputs which trigger the same bug.
Both need to write the name of the file somewhere, and the structure above allows them to write to separate files, avoiding contention on a single file.

# Extending the Framework #
To use the framework for a particular language or particular kind of equivalence relation, you'll need to:

1. Extend `ParseResult` with whatever parsed representation you want for your purposes
2. Extend `Parser` to be able to parse in `ParseResult`s of the kind you want
3. Put your parser in `ParserIndex`, along with some usable name
4. Pass your usable name as a parameter when running


