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
from collections import defaultdict

def getCMD(param_path):
    ROOT="/Users/jiaqiangruan/Projects/SearchEngine/Homeworks"
    HW="HW5"
    LIB=ROOT+"/lucene-8.1.1"
    CLASSPATH="%s/*:%s/%s" % (LIB, ROOT, HW)
    PARAM_DIR="%s/%s/PARAM_DIR" %(ROOT, HW)
    cmd = "java -classpath %s QryEval %s/%s" % (CLASSPATH, PARAM_DIR, param_path)
    return cmd


def get_trec_eval(output_path):
    userId = 'jruan@andrew.cmu.edu'
    password = 'Fu5P5isZ'
    hwId = 'HW5'
    qrels = 'cw09a.adhoc.1-200.qrel.indexed'

    #  Form parameters - these must match form parameters in the web page

    url = 'https://boston.lti.cs.cmu.edu/classes/11-642/HW/HTS/tes.cgi'
    values = { 'hwid' : hwId,				# cgi parameter
               'qrel' : qrels,				# cgi parameter
               'logtype' : 'Detailed',			# cgi parameter
               'leaderboard' : 'No'				# cgi parameter
               }

    #  Make the request

    files = {'infile' : (output_path, open(output_path, 'rb')) }	# cgi parameter
    result = requests.post (url, data=values, files=files, auth=(userId, password))

    #  Replace the <br /> with \n for clarity

    # print (result.text.replace ('<br />', '\n'))
    data = result.text
    # print(data)
    # while 1: pass
    # for line in data:
    #     if "P_10" in line:
    #         print(line)
    
    data = result.text
    data = data[data.index('<pre>'):data.index('</pre>')]
    data = data.split('\n')

    print("--output: %s--\n"%output_path)
    for line in data:
        print(line.strip())
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

def get_ndeval_eval(output_path):
    userId = 'jruan@andrew.cmu.edu'
    password = 'Fu5P5isZ'

    #  Form parameters - these must match form parameters in the web page

    url = 'https://boston.lti.cs.cmu.edu/classes/11-642/HW/HTS/nes.cgi'
    values = {'qrel' : 'cw09a.diversity.101-200.qrel.indexed',
              'hwid' : 'HW5'
             }


    #  Make the request

    files = {'infile' : (output_path, open(output_path, 'rb')) }
    result = requests.post (url, data=values, files=files, auth=(userId, password))

    data = result.text
    
    data = data[data.index('<pre>')+len('<pre>'):data.index('</pre>')].strip()
    data = data.split('\n')
    print(data)
    ans = defaultdict(dict)
    for line in data[1:]:
        tokens = line.split(',')

        if len(tokens) !=23 :continue
        ans[tokens[1]]['P-IA@10'] = float(tokens[18])
        ans[tokens[1]]['P-IA@20'] = float(tokens[19])
        ans[tokens[1]]['aNDCG@20'] = float(tokens[13])

    return ans



total = {}


# indexes = ["1a", "1b", "1c"]
# indexes = ["2a","2b","2c","2d"]
# indexes = ["3a","3b","3c","3d"]
# indexes = ["4a","4b", "4c"]
indexes = ["2.1a"]

for index in indexes:
    params_path = "HW5-Exp-%s.param" % index
    output_path = 'OUTPUT_DIR/HW5-Exp-%s.teIn' % index
    os.system(getCMD(params_path))
    tmp = get_trec_eval(output_path)
    total[index] = tmp
    get_ndeval_eval(output_path)

# print(total)

# with open("result-Exp1.csv", "w+") as f:
#     all_exp = indexes
#     first_line = ",".join(all_exp)
#     f.write(first_line+"\n")

#     metrics = ["P@10","P@20","P@30","ndcg_cut_10","ndcg_cut_20","ndcg_cut_30","MAP"]
#     for m in metrics:
#         res = []
#         for index in all_exp:
#             res.append("%.4f"%total[index][m])
#         line = ",".join(res)
#         f.write(line+"\n")