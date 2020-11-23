Introduction
============

These instructions can be used to run Pythia, the tool implementing
the analysis of our ECOOP’20 paper entitled “Static Analysis of Shape 
in TensorFlow Programs”[1].

You can find 
* our paper: https://doi.org/10.4230/LIPIcs.ECOOP.2020.15
* the self-contained artifact reproducing the paper's experiments, 
  evaluated by the ECOOP'20 AEC: https://doi.org/10.4230/DARTS.6.2.6

As an example we show how to run the analysis on a program from the dataset of
the ISSTA 2018 paper “An empirical study on TensorFlow program bugs”[2].
Their dataset can be cloned from <https://github.com/ForeverZyh/TensorFlow-Program-Bugs>.

The compilation of the analysis binary under each configuration should
take about 10 minutes.

Running Pythia on a single file
-------------------------------

A single file can be analyzed by running the following command:

        doop -a <analysis_sensitivity> -i <input_file> -id <analysis_id> --platform python_2 --single-file-analysis --tensor-shape-analysis --full-tensor-precision

Flags used in the above command explained:

-   `-a`: The context sensitivy of the analysis, available options:
    `context-insensitive`, `1-call-site-sensitive`,
    `1-call-site-sensitive+heap`

-   `-i`: The file or files to be analyzed.

-   `-id`: The analysis id.

-   `–single-file-analysis`: Flag for performing optimizations when the
    analysis input set is a single file.

-   `–tensor-shape-analysis`: Flag enabling the tensor shape
    analysis.

-   `–full-tensor-precision`: Flag enabling the precise tensor value
    abstraction described in section 5.3 of our paper.


### Example

We provide an end-to-end example of running Pythia on UT1, using the
most precise analysis configuration.

        doop -a 1-call-site-sensitive+heap -i [TensorFlow-Program-Bugs-Dir]/StackOverflow/UT-1/38167455-buggy/mnist.py -id ut1 --platform python_2 --single-file-analysis --tensor-shape-analysis --full-tensor-precision

The messages displayed below are expected and do not indicate an error:

    PYTHON_FACT_GEN: Error reading Core[Root] source file name:null
    PYTHON_FACT_GEN: Error reading Core[Exception] source file name:null
    PYTHON_FACT_GEN: Error reading Core[CodeBody] source file name:null
    PYTHON_FACT_GEN: Error reading Core[lambda] source file name:null
    PYTHON_FACT_GEN: Error reading Core[filter] source file name:null
    PYTHON_FACT_GEN: Error reading Core[comprehension] source file name:null
    PYTHON_FACT_GEN: Error reading Core[object] source file name:null
    PYTHON_FACT_GEN: Error reading Core[list] source file name:null
    PYTHON_FACT_GEN: Error reading Core[set] source file name:null
    PYTHON_FACT_GEN: Error reading Core[dict] source file name:null
    PYTHON_FACT_GEN: Error reading Core[tuple] source file name:null
    PYTHON_FACT_GEN: Error reading Core[trampoline] source file name:null

The analysis execution should end with the printing of the analysis
analytics:

    analysis compilation time (sec)                               790.000000
    analysis execution time (sec)                                 0.258000
    disk footprint (KB)                                           6,385.000000
    wala-fact-generation time (sec)                               1.000000
    var points-to (INS)                                           291
    var points-to (SENS)                                          367
    reachable variables (INS)                                     161
    reachable variables (SENS)                                    239
    reachable methods (INS)                                       22
    reachable methods (SENS)                                      38
    call graph edges (INS)                                        37
    call graph edges (SENS)                                       51
    tensor op produces output (INS)                               23
    tensor op produces output (SENS)                              37
    tensor op error (INS)                                         0
    tensor op error (SENS)                                        0
    tensor op warning (INS)                                       1
    tensor op warning (SENS)                                      1
    tensor has more than one shape (INS)                          0
    tensor has more than one shape (SENS)                         0
    tensor shape has imprecise contents (INS)                     0
    tensor shape has imprecise contents (SENS)                    0
    tensor op has imprecise name (INS)                            0
    tensor op has imprecise name (SENS)                           0
    tensor op leads to circle (INS)                               0
    tensor op leads to circle (SENS)                              0


References
----------
[1]
Sifis Lagouvardos, Julian Dolby, Neville Grech, Anastasios Antoniadis,
Yannis Smaragdakis, *Static Analysis of Shape in TensorFlow Programs*,
34th European Conference on Object-Oriented Programming, 2020.

[2]
Yuhao Zhang, Yifan Chen, Shing-Chi Cheung, Yingfei Xiong, Lu Zhang,
*An Empirical Study on TensorFlow Program Bugs* Proceedings of the 27th
ACM SIGSOFT International Symposium on Software Testing and Analysis, 2018.
