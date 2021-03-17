#!/usr/bin/python

#
#  This python script illustrates fetching information from a CGI program
#  that typically gets its data via an HTML form using a POST method.
#
#  Copyright (c) 2018, Carnegie Mellon University.  All Rights Reserved.
#
import os
import requests
import pickle as pkl

def getCMD(param_path):
    ROOT="/Users/jiaqiangruan/tmp/SearchEngine"
    HW="HW3"
    LIB=ROOT+"/lucene-8.1.1"
    CLASSPATH="%s/*:%s/%s/bin" % (LIB, ROOT, HW)
    cmd = "java -classpath %s QryEval %s" % (CLASSPATH, param_path)
    return cmd


def test(output_path):
    userId = 'jruan@andrew.cmu.edu'
    password = 'Fu5P5isZ'
    hwId = 'HW3'
    qrels = 'topics.701-850.qrel'

    #  Form parameters - these must match form parameters in the web page

    url = 'https://boston.lti.cs.cmu.edu/classes/11-642/HW/HTS/tes.cgi'
    values = { 'hwid' : hwId,				# cgi parameter
               'qrel' : qrels,				# cgi parameter
               'logtype' : 'Summary',			# cgi parameter
               'leaderboard' : 'No'				# cgi parameter
               }

    #  Make the request

    files = {'infile' : (output_path, open(output_path, 'rb')) }	# cgi parameter
    result = requests.post (url, data=values, files=files, auth=(userId, password))

    #  Replace the <br /> with \n for clarity

    # print (result.text.replace ('<br />', '\n'))
    # data = result.text.split('<br />')
    # for line in data:
    #     if "P_10" in line:
    #         print(line)
    data = result.text
    data = data[data.index('<pre>'):data.index('</pre>')]
    data = data.split('\n')
    ans = {}
    for line in data:
        if 'P_10 ' in line:
            ans['P@10'] = float(line.split()[2])
        if 'P_20 ' in line:
            ans['P@20'] = float(line.split()[2])
        if 'P_30 ' in line:
            ans['P@30'] = float(line.split()[2])
        if 'map ' in line:
            ans['MAP'] = float(line.split()[2])
    return ans


# param_text = """indexPath=INPUT_DIR/index-gov2
# queryFilePath=TEST_DIR/HW2-Exp-2.1c.qry
# trecEvalOutputPath=OUTPUT_DIR/HW2-Exp-2.1c.teIn
# trecEvalOutputLength=100
# retrievalAlgorithm=Indri
# Indri:mu=%d
# Indri:lambda=%f"""


ans = {}
for index in ("1a", "1b", "1c"):
    params_path = "TEST_DIR/HW3-Exp-%s.param" % index
    output_path = 'OUTPUT_DIR/HW3-Exp-%s.teIn' % index
    os.system(getCMD(params_path))
    tmp = test(output_path)
    ans[index] = tmp

print(ans)