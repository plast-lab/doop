class TestScalerPredictions {

	static void main(String[] args) {
		// read all the lines into a list, each line is an element in the list
		File scalerFile = new File("scaler-context.csv")
		def scalerLines = scalerFile.readLines()

		def scalerPredictions = [:]
		def scalerContexts = [:]
		for (line in scalerLines) {
			def parts = line.trim().split('\t')
			scalerPredictions.put(parts[0], Integer.parseInt(parts[2]))
			scalerContexts.put(parts[0], parts[1])

		}

		File actualFile = new File("actual-context.csv")
		def actualLines = actualFile.readLines()

		def actualContexts = [:]
		for (line in actualLines) {
			def parts = line.trim().split('\t')
			actualContexts.put(parts[0], Integer.parseInt(parts[1]))
		}

		for (String method : scalerPredictions.keySet()) {
			if (actualContexts.containsKey(method)) {
				if (actualContexts.get(method) > scalerPredictions.get(method)) {
					System.out.println("Method " + method + " predicted bound: " + scalerPredictions.get(method) + " actual # of contexts: " + actualContexts.get(method) + " analysis: " + scalerContexts.get(method))
				}
			}
			else {
				System.out.println("Unreachable method " + method)
			}
		}
	}
}
