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
exist = lambda x, y: nx.has_path(G, x, y)
path = lambda x, y: nx.shortest_path(G, x, y)
grep = lambda txt, flt: [x for x in txt if all([(y in x) for y in [[flt], flt][type(flt) is list]])]

def read_taint_flow_vars(x):
    with open(x) as f:
        flows = [['{}@{}'.format(z[1],z[0]), '{}@{}'.format(z[3],z[2])] for z in [y.split("\t") for y in f.read().strip().split("\n")]]
        flows = [x for x in flows if G.has_node(x[0]) and G.has_node(x[1])]
        return flows

vars_to_flows = lambda x: sorted([path(*y) for y in x if exist(*y)], key=len)

print "nodes() returns all nodes in the graph"
print "all_paths() returns all paths"
print "path_from(x) returns paths from x"
print "path_to(x) returns paths to x"
print "path(x, y) returns paths from x to y"
print "exist(x, y) returns if a path exists from x to y"
print "grep(x, y) returns strings of list x that contain all strings in list y (y can also be a string)"
print "read_taint_flow_vars(f) reads file f (should be in LeakingTaintedInformationVars.csv form) and returns a list of from-to for leaking information pairs"
print "vars_to_flows(x) takes a list of from-to pairs and returns the flows for these vars sorted by length"

flows = None
if len(sys.argv) > 2:
    fp = sys.argv[2]
    print "Reading `flows` from: " + fp
    flows = vars_to_flows(read_taint_flow_vars(fp))

ipdb.set_trace()
