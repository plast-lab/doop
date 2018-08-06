import sys
import ipdb
import networkx as nx

# This script reads a csv file of the format:
# fromContext fromVar toContext toVar
# and creates a graph of the flow of values, using as edges:
# fromVar@fromContext -> toVar@toContext
# Some methods are provided below to help navigate the graph

# might need: pip install --user ipdb networkx

G = nx.DiGraph()
print "reading file..."
with open(sys.argv[1], 'r') as f:
    for i in f.read().strip().split("\n"):
        x = i.split("\t")
        frm = "{}@{}".format(x[1], x[0])
        to = "{}@{}".format(x[3], x[2])
        G.add_path([frm, to])

nodes = lambda: G.nodes()
all_paths = lambda: nx.all_pairs_shortest_path(G)
path_from = lambda x: nx.shortest_path(G, x)
path_to = lambda x: nx.shortest_path(G, target=x)
path = lambda x, y: nx.shortest_path(G, x, y)
grep = lambda txt, flt: [x for x in txt if all([(y in x) for y in [[flt], flt][type(flt) is list]])]


print "nodes() returns all nodes in the graph"
print "all_paths() returns all paths"
print "path_from(x) returns paths from x"
print "path_to(x) returns paths to x"
print "path(x, y) returns paths from x to y"
print "grep(x, y) returns strings of list x that contain all strings in list y (y can also be a string)"

ipdb.set_trace()
