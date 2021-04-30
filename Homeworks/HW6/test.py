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
    ROOT="/Users/jiaqiangruan/Projects/SearchEngine/Homeworks"
    HW="HW6"
    LIB=ROOT+"/lucene-8.1.1"
    CLASSPATH="%s/*:%s/%s" % (LIB, ROOT, HW)
    PARAM_DIR="%s/%s" %(ROOT, HW)
    cmd = "java -classpath %s QryEval %s/%s" % (CLASSPATH, PARAM_DIR, param_path)
    return cmd


def test(output_path):
    userId = 'jruan@andrew.cmu.edu'
    password = 'Fu5P5isZ'
    hwId = 'HW6'
    qrels = "cw09a.adhoc.1-200.qrel.indexed"

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
    # print(data)
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
        if 'ndcg_cut_10 ' in line:
            ans['ndcg_cut_10'] = float(line.split()[2])
        if 'ndcg_cut_20 ' in line:
            ans['ndcg_cut_20'] = float(line.split()[2])
        if 'ndcg_cut_30 ' in line:
            ans['ndcg_cut_30'] = float(line.split()[2])
    return ans


# param_text = """indexPath=INPUT_DIR/index-gov2
# queryFilePath=TEST_DIR/HW2-Exp-2.1c.qry
# trecEvalOutputPath=OUTPUT_DIR/HW2-Exp-2.1c.teIn
# trecEvalOutputLength=100
# retrievalAlgorithm=Indri
# Indri:mu=%d
# Indri:lambda=%f"""

params_path = "PARAM_DIR/HW6-Train-0.param"
os.system(getCMD(params_path))

# ans = {}
# # for index in ("1a", "1b", "1c", "3a", "3b", "3c", "3d"):
# indexes = ("5.1a","5.1b","5.1c","5.1d") #("4.1a","4.1b","4.1c","4.1d",)#"3.1a","3.1b","3.1c","3.1d","3.1e","3.2a","3.2b","3.2c","3.2d","3.2e",) #,"2.1b","2.1c","2.1d",):
# for index in indexes:
#     params_path = "PARAM_DIR/HW6-Exp-%s.param" % index
#     output_path = 'OUTPUT_DIR/HW6-Exp-%s.teIn' % index
#     os.system(getCMD(params_path))
#     # tmp = test(output_path)
#     # ans[index] = tmp

# for index in indexes:
#     print("=========%s========" %index)
#     for metrix in ('P@10','P@20','P@30', 'ndcg_cut_10', 'ndcg_cut_20', 'ndcg_cut_30', 'MAP'):
#         print(ans[index][metrix])
#
# with open('exp2.pkl', 'wb') as f:
#     pkl.dump(ans, f)
#
# with open('exp2.pkl', 'rb') as f:
#     ans = pkl.load(f)
#     print(ans)