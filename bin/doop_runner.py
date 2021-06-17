import subprocess
import os.path
import os

REFL_PARAMS = ['--reflection-classic', '--reflection-substring-analysis', '--reflection-speculative-use-based-analysis']

class DoopRunner:
    def __init__(self, platform, dry, default_params = []):
        self.platform = platform
        self.dry = dry
        self.default_params = default_params

    def run_doop(self, analysis_id, additional_params):
        out_db = ('out/' + analysis_id + '/database')
        if self.dry:
            return 0, out_db
        all_params = (
            ['./doop', '--platform', self.platform,
             # '--distinguish-all-string-buffers', '--generate-jimple',
             '--id', analysis_id, '--timeout', '20']
            + self.default_params + additional_params
        )
        print(' '.join(all_params))
        process = subprocess.run(
            all_params, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, universal_newlines=True
        )
        print(process.stdout)
        if 'BUILD SUCCESSFUL' not in process.stdout:
            print(process.stdout)
            raise Exception('Unsuccessful Run')
        seconds = 0.0
        stdout = process.stdout
        seconds += float(stdout.split('analysis execution time (sec)')[1].split('\n')[0].strip())
        print('%d seconds'%seconds)
        return seconds, out_db

def parseleaks(db):
    return Query('LeakingTaintedInformation', 2, [3,4]).getResults(db)

class Query:
    def __init__(self, souffle, size, souffle_idx = None):
        self.souffle_idx = souffle_idx
        self.souffle = souffle
        self.size = size

    def getResults(self, db):
        idx = range(self.size) if self.souffle_idx is None else self.souffle_idx
        out = set()
        try:
            with open('%s/%s.csv'%(db, self.souffle)) as f:
                for line in f:
                    linesplit = line.split('\t')
                    linesplit = [linesplit[id].strip() for id in idx]
                    assert len(linesplit) == self.size
                    out.add(tuple(linesplit))
        except:
            print("WARNING: Could not read file: " + db)
        return out
