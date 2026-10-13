import { describe,expect,test } from 'vitest';
import { filterAndSortRows,pageRows } from './tableModel';

const rows=[{id:'2',name:'Market',status:'OPEN'},{id:'1',name:'Civic Hall',status:'CLOSED'},{id:'3',name:'Market Annex',status:'OPEN'}];

describe('admin table model',()=>{
  test('filters by text/status and sorts deterministically',()=>{
    expect(filterAndSortRows(rows,{query:'market',status:'OPEN',sortKey:'id'}).map((row)=>row.id)).toEqual(['2','3']);
    expect(filterAndSortRows(rows,{direction:'desc',sortKey:'name'}).map((row)=>row.name)).toEqual(['Market Annex','Market','Civic Hall']);
  });
  test('paginates locally and rejects invalid pagination',()=>{
    expect(pageRows(rows,1,2)).toEqual([{id:'3',name:'Market Annex',status:'OPEN'}]);
    expect(()=>pageRows(rows,-1,2)).toThrow(RangeError);
  });
});
