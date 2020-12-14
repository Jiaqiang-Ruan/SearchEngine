#!/usr/bin/perl

#
# Perl subroutine that generates Indri dependence model queries.
#
# Written by: Don Metzler (metzler@cs.umass.edu)
# Last update: 06/27/2005
#
# Feel free to distribute, edit, modify, or mangle this code as you see fit. If you make any interesting
# changes please email me a copy.
#
# For more technical details, see:
#
#    * Metzler, D. and Croft, W.B., "A Markov Random Field Model for Term Dependencies," ACM SIGIR 2005.
#
#    * Metzler, D., Strohman T., Turtle H., and Croft, W.B., "Indri at TREC 2004: Terabyte Track", TREC 2004.
#
#    * http://ciir.cs.umass.edu/~metzler/
#
# MODIFICATIONS
#  - Updated by Jamie Callan:  02/11/2015
#    Modified to support a less cryptic Indri-like query language.
#    #combine --> #and, #1 --> #near/1, #weight --> #wand, and #uw --> #window/
#
# NOTES
#
#    * this script assumes that the query string has already been parsed and that all characters
#      that are not compatible with Indri's query language have been removed.
#
#    * it is not advisable to do a 'full dependence' variant on long strings because of the exponential
#      number of terms that will result. it is suggested that the 'sequential dependence' variant be
#      used for long strings. either that, or split up long strings into smaller cohesive chunks and
#      apply the 'full dependence' variant to each of the chunks.
#
#    * the unordered features use a window size of 4 * number of terms within the phrase. this has been
#      found to work well across a wide range of collections and topics. however, this may need to be
#      modified on an individual basis.
#

# example usage
my ($a, $b, $c) = @ARGV;

#print "711: ";
#print formulate_query( "Train station security measures", "sd", $a, $b, $c ) . "\n";
#print "730: ";
#print formulate_query( "Gastric bypass complications", "sd", $a, $b, $c ) . "\n";
#print "733: ";
#print formulate_query( "Airline overbooking", "sd", $a, $b, $c ) . "\n";
#print "751: ";
#print formulate_query( "Scrabble Players", "sd", $a, $b, $c ) . "\n";
#print "758: ";
#print formulate_query( "Embryonic stem cells", "sd", $a, $b, $c ) . "\n";
#print "764: ";
#print formulate_query( "Increase mass transit use", "sd", $a, $b, $c ) . "\n";
#print "802: ";
#print formulate_query( "Volcano eruptions global temperature", "sd", $a, $b, $c ) . "\n";
#print "809: ";
#print formulate_query( "wetlands  wastewater  treatment", "sd", $a, $b, $c ) . "\n";
#print "811: ";
#print formulate_query( "handwriting recognition", "sd", $a, $b, $c ) . "\n";
#print "826: ";
#print formulate_query( "Florida Seminole Indians", "sd", $a, $b, $c ) . "\n";


print "63: ";
print formulate_query("flushing", "sd", $a, $b, $c) . "\n";
print "65: ";
print formulate_query("korean language", "sd", $a, $b, $c) . "\n";
print "67: ";
print formulate_query("vldl levels", "sd", $a, $b, $c) . "\n";
print "83: ";
print formulate_query("memory", "sd", $a, $b, $c) . "\n";
print "85: ";
print formulate_query("milwaukee journal sentinel", "sd", $a, $b, $c) . "\n";
print "87: ";
print formulate_query("who invented music", "sd", $a, $b, $c) . "\n";
print "91: ";
print formulate_query("er tv show", "sd", $a, $b, $c) . "\n";
print "97: ";
print formulate_query("south africa", "sd", $a, $b, $c) . "\n";
print "99: ";
print formulate_query("satellite", "sd", $a, $b, $c) . "\n";
print "103: ";
print formulate_query("madam cj walker", "sd", $a, $b, $c) . "\n";
print "105: ";
print formulate_query("sonoma county medical services", "sd", $a, $b, $c) . "\n";
print "107: ";
print formulate_query("cass county missouri", "sd", $a, $b, $c) . "\n";
print "111: ";
print formulate_query("lymphoma in dogs", "sd", $a, $b, $c) . "\n";
print "117: ";
print formulate_query("dangers of asbestos", "sd", $a, $b, $c) . "\n";
print "119: ";
print formulate_query("interview thank you", "sd", $a, $b, $c) . "\n";
print "123: ";
print formulate_query("von willebrand disease", "sd", $a, $b, $c) . "\n";
print "125: ";
print formulate_query("butter and margarine", "sd", $a, $b, $c) . "\n";
print "129: ";
print formulate_query("iowa food stamp program", "sd", $a, $b, $c) . "\n";
print "131: ";
print formulate_query("equal opportunity employer", "sd", $a, $b, $c) . "\n";
print "137: ";
print formulate_query("rock and gem shows", "sd", $a, $b, $c) . "\n";
print "139: ";
print formulate_query("rocky mountain news", "sd", $a, $b, $c) . "\n";
print "141: ";
print formulate_query("va dmv registration", "sd", $a, $b, $c) . "\n";
print "145: ";
print formulate_query("vines for shade", "sd", $a, $b, $c) . "\n";
print "147: ";
print formulate_query("tangible personal property tax", "sd", $a, $b, $c) . "\n";
print "149: ";
print formulate_query("uplift at yellowstone national park", "sd", $a, $b, $c) . "\n";


#
# formulates a query based on query text and feature weights
#
# arguments:
#    * query - string containing original query terms separated by spaces
#    * type  - string. "sd" for sequential dependence or "fd" for full dependence variant. defaults to "fd".
#    * wt[0] - weight assigned to term features
#    * wt[1] - weight assigned to ordered (#near) features
#    * wt[2] - weight assigned to unordered (#window) features
#
sub formulate_query {
    my ( $q, $type, @wt ) = @_;

    # trim whitespace from beginning and end of query string
    $q =~ s/^\s+|\s+$//g;

    my $queryT = "#and( ";
    my $queryO = "#and(";
    my $queryU = "#and(";

    # generate term features (f_T)
    my @terms = split(/\s+/ , $q);
    my $term;
    foreach $term ( @terms ) {
	$queryT .= "$term ";
    }

    my $num_terms = @terms;

    # skip the rest of the processing if we're just
    # interested in term features or if we only have 1 term
    if( ( $wt[1] == 0.0 && $wt[2] == 0.0 ) || $num_terms == 1 ) {
	return $queryT . ")";
    }

    # generate the rest of the features
    my $start = 1;
    if( $type eq "sd" ) { $start = 3; }
    for( my $i = $start ; $i < 2 ** $num_terms ; $i++ ) {
	my $bin = unpack("B*", pack("N", $i)); # create binary representation of i
	my $num_extracted = 0;
	my $extracted_terms = "";

	# get query terms corresponding to 'on' bits
	for( my $j = 0 ; $j < $num_terms ; $j++ ) {
	    my $bit = substr($bin, $j - $num_terms, 1);
	    if( $bit eq "1" ) {
		$extracted_terms .= "$terms[$j] ";
		$num_extracted++;
	    }
	}

	if( $num_extracted == 1 ) { next; } # skip these, since we already took care of the term features...
	if( $bin =~ /^0+11+[^1]*$/ ) { # words in contiguous phrase, ordered features (f_O)
	    $queryO .= " #near/1( $extracted_terms) ";
	}
	$queryU .= " #window/" . 4*$num_extracted . "( $extracted_terms) "; # every subset of terms, unordered features (f_U)
	if( $type eq "sd" ) { $i *= 2; $i--; }
    }

    my $query = "#wand(";
    if( $wt[0] != 0.0 && $queryT ne "#and( " ) { $query .= " $wt[0] $queryT)"; }
    if( $wt[1] != 0.0 && $queryO ne "#and(" ) { $query .= " $wt[1] $queryO)"; }
    if( $wt[2] != 0.0 && $queryU ne "#and(" ) { $query .= " $wt[2] $queryU)"; }

    if( $query eq "#wand(" ) { return ""; } # return "" if we couldn't formulate anything

    return $query . " )";
}