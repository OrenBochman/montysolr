

import os
import thread
import sys
import cStringIO
import zlib

from invenio import search_engine
from invenio import search_engine_summarizer
from invenio import dbquery
from invenio import bibrecord
from invenio.intbitset import intbitset
from invenio import bibrank_citation_searcher as bcs



from StringIO import StringIO as sIO

def dispatch(func_name, *args, **kwargs):
    """Dispatches the call to the *local* worker
    It returns a tuple (ThreadID, result)
    """
    tid = thread.get_ident()
    out = globals()[func_name](*args, **kwargs)
    return [tid, out]


def get_recids_changes(last_recid, max_recs=10000, mod_date=None, table='bibrec'):
    """
    Retrieves the sets of records that were added/updated/deleted
    
    The time is selected according to some know recid, ie. 
    we retrieve the modification time of one record and look
    at those that are older.
    
        OR
        
    You can pass in the date that you are interested in, in the 
    format: YYYY-MM-DD HH:MM:SS
    
    added => bibrec.modification_date == bibrec.creation_date
    updated => bibrec.modification_date >= bibrec.creation_date
    deleted => bibrec.status == DELETED
    """
    table = dbquery.real_escape_string(table)
    search_op = '>'    
    if not mod_date:
        if last_recid == -1:
            l = list(dbquery.run_sql("SELECT modification_date FROM `%s` ORDER BY modification_date ASC LIMIT 1" % (table,)))
            mod_date = l[0][0].strftime(format="%Y-%m-%d %H:%M:%S")
            search_op = '>='
        else:
            # let's make sure we have a valid recid (or get the close valid one)
            l = list(dbquery.run_sql("SELECT id, modification_date FROM `" + table + "` WHERE id >= %s LIMIT 1", (last_recid,)))
            if not len(l):
                return
            last_recid = l[0][0]
            mod_date = l[0][1].strftime(format="%Y-%m-%d %H:%M:%S")
            
            # there is not api to get this (at least i haven't found it)
            #mod_date = search_engine.get_modification_date(last_recid, fmt="%Y-%m-%d %H:%i:%S")
            #if not mod_date:
            #    return
        
    modified_records = list(dbquery.run_sql("SELECT id,modification_date, creation_date FROM `" + table +
                    "` WHERE modification_date " + search_op + "\"%s\" ORDER BY modification_date ASC, id ASC LIMIT %s" %
                    (mod_date, max_recs )))
    
    #sys.stderr.write(str(("SELECT id,modification_date, creation_date FROM bibrec "
    #                "WHERE modification_date " + search_op + "\"%s\" ORDER BY modification_date ASC, id ASC LIMIT %s" %
    #                (mod_date, max_recs ))) + "\n")
    #print len(modified_records)
    
    if not len(modified_records):
        return
    
    added = []
    updated = []
    deleted = []
    
    dels = {}
    for x in list(dbquery.run_sql("""SELECT distinct(id_bibrec) FROM bibrec_bib98x WHERE 
        id_bibrec >= %s AND id_bibrec <= %s AND 
        id_bibxxx=(SELECT id FROM bib98x WHERE VALUE='%s')""" % (modified_records[0][0],
                                                          modified_records[-1][0],
                                                          'DELETED'))):
        dels[int(x[0])] = 1
    
    
    for recid, mod_date, create_date in modified_records:
        recid = int(recid)
        
        # this is AWFULLY slow! 100x times
        #status = search_engine.record_exists(recid)
        
        if recid in dels:
        #if status == -1:
            deleted.append(recid)
        elif mod_date == create_date:
            added.append(recid)
        else:
            updated.append(recid)
    
    return {'DELETED': deleted, 'UPDATED': updated, 'ADDED': added}, recid, str(mod_date)



def citation_summary(recids, of, ln, p, f):
    out = ReqStringIO()
    x = search_engine_summarizer.summarize_records(recids, of, ln, p, f, out)
    if x:
        output = x
    else:
        out.seek(0)
        output = out.read()
    return output

def invenio_search(kwargs):
    """This search uses Invenio API to retrieve anything"""
    
    # because of invenio bug, sanity checking of rg is wrong
    if 'rg' in kwargs:
        kwargs['rg'] = int(kwargs['rg'])
    
    req = cStringIO.StringIO()
    result = search_engine.perform_request_search(req=req, **kwargs)
    data = None
    if not result:
        req.seek(0)
        data = req.read()
    req.close()
    if result:
        return result
    if data:
        return data
    

def invenio_search_xml(kwargs):
    """Simple version which just fetches XML records
    from Invenio. It only understands query of type:
    p=recid:1->50 OR recid:50 OR recid:....
    
    Unfortunately, we cannot use 'print_records' because
    that one (for strange reasons) creates a range out 
    of recIDS. And I don't want to use bibformat, 
    because bibformat is not working nicely with 
    strings (it is slower)
    
    """
    out = []
    p = kwargs['p']
    of = 'xm'
    if 'of' in kwargs:
        of = kwargs['of']

    if of == 'xm':
        out.append('<?xml version="1.0" encoding="UTF-8"?>')
        out.append('<collection xmlns="http://www.loc.gov/MARC21/slim">')
    
    clauses = p.split(' OR ')
    for c in clauses:
        c = c.replace('recid:', '')
        if '->' in c:
            ints = c.split('->')
            for x in xrange(int(ints[0]), int(ints[1])+1):
                out.append(search_engine.print_record(x, format=of))
        else:
            out.append(search_engine.print_record(int(c), format=of))
    if of == 'xm':
        out.append('</collection>')
    
    return '\n'.join(out)
    

def invenio_search_xml2(kwargs):
    """Simple version which just fetches XML records
    from Invenio. It only understands query of type:
    p=recid:1->50 OR recid:50 OR recid:....
    
    This is an optimized version
    
    """
    out = []
    p = kwargs['p']
    of = 'xm'
    if 'of' in kwargs:
        of = kwargs['of']

    if of == 'xm':
        out.append('<?xml version="1.0" encoding="UTF-8"?>')
        out.append('<collection xmlns="http://www.loc.gov/MARC21/slim">')
    
    query = ['SELECT id_bibrec, `value` FROM bibfmt WHERE `format`=\'xm\' AND']
    
    # first expand the recids
    clauses = p.split(' OR ')
    ccs = []
    for c in clauses:
        c = c.replace('recid:', '')
        if '->' in c:
            ints = c.split('->')
            ccs.append('(id_bibrec>=%s AND id_bibrec<=%s)' % tuple(ints))
        else:
            ccs.append('(id_bibrec=%s)' % c)
    
    query.append('(')
    query.append(' OR '.join(ccs))
    query.append(')')
    
    
    query = ' '.join(query)    
    # now retrieve the record xml
    decompress = zlib.decompress
    for value in dbquery.run_sql(query):
        ### In case of corruption, we fail (intentionally)
        out.append(decompress(value[1]))
            
    
    if of == 'xm':
        out.append('</collection>')
    
    return '\n'.join(out)



def search(q, max_len=25, offset=0):
    #hits = search_engine.search_pattern_parenthesised(None, q)
    hits = search_engine.perform_request_search(None, p=q)
    total_matches = len(hits)

    if max_len:
        return [offset, hits[:max_len], total_matches]
    else:
        return [offset, hits, total_matches]


def sort_and_format(hits, kwargs):

    kwargs = search_engine.prs_wash_arguments(**kwargs)
    t1 = os.times()[4]
    req = ReqStringIO()  # new ver of Invenio is looking at args
    kwargs['req'] = req

    if 'hosted_colls_actual_or_potential_results_p' not in kwargs:
        kwargs['hosted_colls_actual_or_potential_results_p'] = True # this prevents display of the nearest-term box

    # search stage 4 and 5: intersection with collection universe and sorting/limiting
    output = search_engine.prs_intersect_with_colls_and_apply_search_limits(hits, kwargs=kwargs, **kwargs)
    if output is not None:
        req.seek(0)
        return req.read() + output

    t2 = os.times()[4]
    cpu_time = t2 - t1
    kwargs['cpu_time'] = cpu_time

    recids = search_engine.prs_rank_results(kwargs=kwargs, **kwargs)

    if 'of' in kwargs and kwargs['of'].startswith('hc'):
        output = citation_summary(intbitset(recids), kwargs['of'], kwargs['ln'], kwargs['p'], kwargs['f'])
        if output:
            return output

    return recids


def get_citation_dict(dictname):
    return bcs.get_citation_dict(dictname)


def create_collection_bibrec(table_name, coll_name, step_size=10000):
    if table_name[0] != '_':
        raise Exception("By convention, temporary tables must begin with '_'. I don't want to give you tools to screw st important")
    
    create_stmt = dbquery.run_sql("SHOW CREATE TABLE bibrec")[0][1].replace('bibrec', dbquery.real_escape_string(table_name))
    dbquery.run_sql("DROP TABLE IF EXISTS `%s`" % dbquery.real_escape_string(table_name))
    dbquery.run_sql(create_stmt)
    
    #now retrieve the collection
    c = search_engine.get_collection_reclist(coll_name)
    if len(c) < 0:
        sys.stderr.write("The collection %s is empty!\n" % coll_name)
    
    c = list(c)
    l = len(c)
    i = 0
    sys.stderr.write("Copying bibrec data\n")
    while i < l:
        dbquery.run_sql("INSERT INTO `%s` SELECT * FROM `bibrec` WHERE bibrec.id IN (%s)" % 
                             (dbquery.real_escape_string(table_name), ','.join(map(str, c[i:i+step_size]))))
        i = i + step_size
        sys.stderr.write("%s\n" % i)
        
    sys.stderr.write("Total number of records: %s\n" % l)
    


class ReqStringIO(sIO):
    '''Because of Invenio insisting to have args inside the 
    req object, we cannot use cStringIO'''
    
    def __init__(self, *args, **kwargs):
        sIO.__init__(self, *args, **kwargs)
        self.args = None
        self.uri = None

if __name__ == '__main__':
    pass
