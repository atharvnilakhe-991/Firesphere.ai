export function filterAndSortRows(rows:Record<string,unknown>[],options:{query?:string;status?:string;sortKey?:string;direction?:'asc'|'desc'}={}):Record<string,unknown>[] {
  const query=options.query?.trim().toLowerCase()??'';
  const filtered=rows.filter((row)=>{
    const matchQuery=!query||Object.values(row).some((value)=>String(value??'').toLowerCase().includes(query));
    const matchStatus=!options.status||options.status==='All'||String(row.status??'')===options.status;
    return matchQuery&&matchStatus;
  });
  if(options.sortKey){const sign=options.direction==='desc'?-1:1;filtered.sort((a,b)=>String(a[options.sortKey!]??'').localeCompare(String(b[options.sortKey!]??''),undefined,{numeric:true,sensitivity:'base'})*sign)}
  return filtered;
}

export function pageRows<T>(rows:T[],page:number,pageSize:number):T[]{
  if(!Number.isInteger(page)||page<0||!Number.isInteger(pageSize)||pageSize<1)throw new RangeError('Page must be zero or greater and page size must be positive.');
  return rows.slice(page*pageSize,(page+1)*pageSize);
}
