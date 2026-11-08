import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from './api/client';
import type { DashboardState, Incident, Measurement, ModelRelease, Site, Device, AuditEntry, Forecast, Inspection } from './api/types';
export function useSites(){return useQuery({queryKey:['sites'],queryFn:async()=>(await api.get<Site[]>('/sites')).data});}
export function useSelectedSite(){const {data:sites=[]}=useSites();const [id,setId]=useState(()=>sessionStorage.getItem('greenpilot.site'));useEffect(()=>{const update=()=>setId(sessionStorage.getItem('greenpilot.site'));window.addEventListener('greenpilot-site-change',update);return()=>window.removeEventListener('greenpilot-site-change',update);},[]);return sites.find(x=>x.id===id)||sites[0];}
export function useDashboard(siteId?:string){return useQuery({queryKey:['site-state',siteId],enabled:!!siteId,queryFn:async()=>(await api.get<DashboardState>(`/sites/${siteId}/state`)).data,refetchInterval:120000});}
export function useIncidents(siteId?:string){return useQuery({queryKey:['incidents',siteId],enabled:!!siteId,queryFn:async()=>(await api.get<Incident[]>('/incidents',{params:{siteId,limit:100}})).data});}
export function useMeasurements(siteId?:string){return useQuery({queryKey:['measurements',siteId],enabled:!!siteId,queryFn:async()=>(await api.get<Measurement[]>('/measurements',{params:{siteId,limit:500}})).data});}
export function useForecasts(siteId?:string){return useQuery({queryKey:['forecasts',siteId],enabled:!!siteId,queryFn:async()=>(await api.get<Forecast[]>('/forecasts',{params:{siteId,limit:200}})).data});}
export function useInspections(siteId?:string){return useQuery({queryKey:['inspections',siteId],enabled:!!siteId,queryFn:async()=>(await api.get<Inspection[]>('/inspections',{params:{siteId,limit:100}})).data});}
export function useDevices(siteId?:string){return useQuery({queryKey:['devices',siteId],enabled:!!siteId,queryFn:async()=>(await api.get<Device[]>('/devices',{params:{siteId}})).data});}
export function useModels(){return useQuery({queryKey:['models'],queryFn:async()=>(await api.get<ModelRelease[]>('/models')).data});}
export function useAudit(siteId?:string){return useQuery({queryKey:['audit',siteId],queryFn:async()=>(await api.get<AuditEntry[]>('/audit',{params:{siteId,limit:100}})).data});}
export function useIrrigationHistory(siteId?:string){return useQuery({queryKey:['irrigation',siteId],enabled:!!siteId,queryFn:async()=>(await api.get<import('./api/types').Command[]>('/irrigation',{params:{siteId,limit:50}})).data});}
