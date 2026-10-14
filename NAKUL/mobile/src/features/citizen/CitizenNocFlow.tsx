import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import type { NocApplication, NocCreateRequest } from '@firesphere/api-client/types';
import { api } from '../../api/client';
import { saveCitizenDraft, loadCitizenDraft } from '../../storage/offlineDatabase';
import { buildNocCreateRequest, NOC_PRESENTATION_STATES, popNocRoute, validateNocCreateFields, type NocDraftFields } from './nocModel';

type Route = 'home' | 'form' | 'documents' | 'details' | 'certificate';
type Draft = NocDraftFields & { documents: LocalDocument[]; previousDraftValues?: Record<string, unknown>; createdApplication?: NocApplication };
type LocalDocument = { id: string; name: string; uri: string; mimeType?: string; size?: number; state: 'PENDING_UPLOAD'|'UPLOADING'|'UPLOAD_FAILED'|'UPLOADED' };
const draftKey = 'citizen:noc:development-draft';
const emptyDraft: Draft = { title: '', buildingName: '', address: '', description: '', documents: [] };

export function CitizenNocFlow({ onClose }: { onClose: () => void }) {
  const [route, setRoute] = useState<Route>('home');
  const [routeHistory, setRouteHistory] = useState<Route[]>([]);
  const [draft, setDraft] = useState<Draft>(emptyDraft);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [savedAt, setSavedAt] = useState('');
  const [busyDoc, setBusyDoc] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [createdNoc, setCreatedNoc] = useState<NocApplication | null>(null);
  const submissionLock = useRef(false);
  useEffect(() => { let active = true; void loadCitizenDraft<Record<string, unknown>>(draftKey).then((value) => { if (active && value) { const restored=restoreDraft(value); setDraft(restored); if (restored.createdApplication) setCreatedNoc(restored.createdApplication); } }).catch(() => { if (active) setError('Your saved draft could not be read. You can still start a new draft.'); }).finally(() => { if (active) setLoading(false); }); return () => { active = false; }; }, []);

  async function saveDraft(): Promise<boolean> {
    try { await saveCitizenDraft(draftKey, draft); setSavedAt(new Date().toLocaleTimeString()); setError(''); return true; }
    catch { setError('Draft could not be saved to encrypted local storage. Check device storage and try again.'); return false; }
  }
  function navigate(next: Route) {
    setRouteHistory((history) => [...history, route]);
    setRoute(next);
  }
  function back() {
    const { route: previous, history } = popNocRoute(routeHistory);
    if (!previous) { onClose(); return; }
    setRouteHistory(history);
    setRoute(previous);
  }
  function edit<K extends keyof Draft>(key: K, value: Draft[K]) { setDraft((current) => ({ ...current, [key]: value })); }
  async function chooseDocument() {
    setBusyDoc(true); setError('');
    try {
      const result = await DocumentPicker.getDocumentAsync({ type: ['application/pdf','image/*'], multiple: false, copyToCacheDirectory: true });
      if (result.canceled) return;
      const asset = result.assets[0];
      if (!asset) return;
      if (!['application/pdf','image/jpeg','image/png','image/webp'].includes(asset.mimeType ?? '')) { setError('Choose a PDF, JPEG, PNG, or WebP file.'); return; }
      if ((asset.size ?? 0) > 10 * 1024 * 1024) { setError('Files must be 10 MB or smaller.'); return; }
      edit('documents', [...draft.documents, { id: `doc-${Date.now()}`, name: asset.name, uri: asset.uri, mimeType: asset.mimeType, size: asset.size, state: 'PENDING_UPLOAD' }]);
    } catch { setError('The file picker could not open. Check permissions and retry.'); }
    finally { setBusyDoc(false); }
  }
  function simulateUpload(id:string){edit('documents',draft.documents.map((doc)=>doc.id===id?{...doc,state:'UPLOADING'}:doc));setTimeout(()=>setDraft((current)=>({...current,documents:current.documents.map((doc)=>doc.id===id&&doc.state==='UPLOADING'?{...doc,state:'UPLOAD_FAILED'}:doc)})),650);}
  async function submitApplication() {
    if (submissionLock.current) return;
    if (createdNoc) { setError('This draft already created a FireSphere application. Start a new draft to submit another application.'); return; }
    const validation = validateNocCreateFields(draft);
    const invalid = Object.values(validation).filter((message): message is string => Boolean(message));
    if (invalid.length) { setError(invalid.join(' ')); return; }
    submissionLock.current = true;
    setSubmitting(true);
    setError('');
    try {
      if (!await saveDraft()) return;
      const payload: NocCreateRequest = buildNocCreateRequest(draft);
      const response = await api.request<unknown>('citizen.application.create', { method: 'POST', body: JSON.stringify(payload) });
      if (!isNocApplication(response)) throw new Error('The NOC service returned an unsupported application response. Your draft is still saved on this device.');
      setCreatedNoc(response);
      const completedDraft = { ...draft, createdApplication: response };
      setDraft(completedDraft);
      try { await saveCitizenDraft(draftKey, completedDraft); }
      catch { setError('FireSphere created the application, but its reference could not be added to the local draft. Keep this application reference before leaving this screen.'); }
      navigate('details');
      Alert.alert('Application created', `FireSphere created ${response.id}. The application status is ${response.status}.`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'The NOC application could not be created. Your draft remains saved on this device.');
    } finally {
      submissionLock.current = false;
      setSubmitting(false);
    }
  }
  const Header = ({ title, subtitle }: { title: string; subtitle: string }) => <><Pressable accessibilityRole="button" onPress={back} style={s.back}><Text style={s.backText}>‹  {route === 'home' ? 'Close NOC' : 'Back'}</Text></Pressable><Text style={s.eyebrow}>CITIZEN · FIRE NOC</Text><Text style={s.title}>{title}</Text><Text style={s.muted}>{subtitle}</Text><View style={s.demo}><Text style={s.demoText}>Drafts are saved on this device. Application submission requires the configured FireSphere API.</Text></View></>;
  if (loading) return <View style={s.center}><ActivityIndicator color="#D32F2F"/><Text style={s.muted}>Loading your local draft…</Text></View>;
  if (route === 'form') return <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={s.page}>
    <Header title="New NOC application" subtitle="Enter the required application details. Your draft remains recoverable if the service is unavailable."/>
    {error ? <State message={error} tone="error"/> : null}
    {draft.previousDraftValues ? <State message="This draft was saved with older fields. Its previous values are retained locally while you fill in the current required fields."/> : null}
    <Field label="Application title" value={draft.title} onChangeText={(v)=>edit('title',v)} placeholder="Fire safety clearance" accessibilityHint="Required by the FireSphere application service"/>
    <Field label="Building name" value={draft.buildingName} onChangeText={(v)=>edit('buildingName',v)} placeholder="Building or premises name" accessibilityHint="Required by the FireSphere application service"/>
    <Field label="Premises address" value={draft.address} onChangeText={(v)=>edit('address',v)} placeholder="Street and locality" accessibilityHint="Required by the FireSphere application service"/>
    <Field label="Description (optional)" value={draft.description} onChangeText={(v)=>edit('description',v)} placeholder="Additional application details" multiline maxLength={2000}/>
    <Text style={s.small}>Only title, building name, address, and optional description are sent to the application service. Selected documents remain local until an upload contract is provided.</Text>
    <Section title="Documents" subtitle="Local selection only · upload is simulated and never starts"/>
    <Pressable accessibilityRole="button" style={s.secondary} onPress={()=>navigate('documents')}><Text style={s.secondaryText}>Manage documents ({draft.documents.length})</Text></Pressable>
    <Pressable accessibilityRole="button" style={s.secondary} onPress={()=>void saveDraft()}><Text style={s.secondaryText}>{savedAt ? `Draft saved at ${savedAt}` : 'Save local draft'}</Text></Pressable>
    <Primary title={createdNoc?'Application created':submitting?'Submitting application…':'Submit application'} onPress={()=>void submitApplication()} disabled={submitting||Boolean(createdNoc)}/>
    <Text style={s.small}>Submission creates a DRAFT application through the configured FireSphere service. Payment and document workflows are separate.</Text>
  </ScrollView>;
  if (route === 'documents') return <ScrollView contentContainerStyle={s.page}>
    <Header title="Documents" subtitle="Select PDF or image files for this local draft."/>
    {error ? <State message={error} tone="error"/> : null}
    <View style={s.info}><Text style={s.infoTitle}>Document requirements are not configured</Text><Text style={s.muted}>No required/optional regulatory document list is available. Selected files stay in the app cache and are not uploaded.</Text></View>
    <Pressable accessibilityRole="button" disabled={busyDoc} style={[s.secondary,busyDoc&&s.disabled]} onPress={()=>void chooseDocument()}><Text style={s.secondaryText}>{busyDoc?'Opening files…':'Choose PDF or image'}</Text></Pressable>
    {!draft.documents.length ? <State message="No documents selected yet."/> : draft.documents.map((doc)=><View key={doc.id} style={s.card}><Text style={s.cardTitle}>{doc.name}</Text><Text style={s.small}>{doc.mimeType ?? 'Unknown type'} · {doc.size ? `${(doc.size/1024/1024).toFixed(1)} MB` : 'Size unavailable'} · {doc.state.replaceAll('_',' ')}</Text><View style={s.row}><Pressable accessibilityRole="button" onPress={()=>Alert.alert('Local file preview', doc.name)}><Text style={s.action}>Preview</Text></Pressable><Pressable accessibilityRole="button" onPress={()=>edit('documents',draft.documents.filter((d)=>d.id!==doc.id))}><Text style={s.danger}>Remove</Text></Pressable><Pressable accessibilityRole="button" onPress={()=>void chooseDocument()}><Text style={s.action}>Replace</Text></Pressable></View>{doc.state==='PENDING_UPLOAD'&&<><Pressable accessibilityRole="button" onPress={()=>simulateUpload(doc.id)}><Text style={s.action}>Simulate upload attempt</Text></Pressable><Pressable accessibilityRole="button" onPress={()=>edit('documents',draft.documents.map((d)=>d.id===doc.id?{...d,state:'UPLOADED'}:d))}><Text style={s.action}>Preview uploaded state only</Text></Pressable></>}{doc.state==='UPLOADING'&&<View style={s.row}><ActivityIndicator color="#D32F2F"/><Text style={s.small}>Development upload simulation…</Text></View>}{doc.state==='UPLOAD_FAILED'&&<View style={s.row}><Text style={s.errorText}>Simulated failure · file remains local</Text><Pressable accessibilityRole="button" onPress={()=>edit('documents',draft.documents.map((d)=>d.id===doc.id?{...d,state:'PENDING_UPLOAD'}:d))}><Text style={s.action}>Retry</Text></Pressable></View>}{doc.state==='UPLOADED'&&<Text style={s.small}>Uploaded state · simulated only, not sent to a service.</Text>}</View>)}
    <Primary title="Save draft" onPress={()=>void saveDraft()}/>
  </ScrollView>;
  if (route === 'certificate') return <ScrollView contentContainerStyle={s.page}><Header title="Certificate & renewal" subtitle="Presentation example only. This app does not generate official certificates or QR verification."/><View style={s.certificate}><Text style={s.watermark}>DEVELOPMENT SAMPLE</Text><Text style={s.cardTitle}>Certificate not issued</Text><Text style={s.muted}>A certificate number, official file, verification code, and renewal date will appear only when provided by the authoritative service.</Text><View style={s.qr}><Text style={s.qrText}>QR</Text></View></View><State message="No official certificate is available for this sample application."/></ScrollView>;
  if (route === 'details') return <ScrollView contentContainerStyle={s.page}><Header title="Application details" subtitle={createdNoc ? 'Server-created application · current state from FireSphere.' : 'Local development record · no official application exists.'}/>{createdNoc ? <View style={s.card}><Pill label="SERVER APPLICATION"/><Text style={s.cardTitle}>{createdNoc.title}</Text><Text style={s.muted}>{createdNoc.buildingName} · {createdNoc.address}</Text><Text style={s.actionTitle}>Status: {createdNoc.status}</Text><Text selectable style={s.small}>Reference: {createdNoc.applicationNumber ?? createdNoc.id}</Text></View> : <View style={s.card}><Text style={s.cardTitle}>Sample application · not submitted</Text><Text style={s.muted}>No server application was created.</Text><Pill label="DEVELOPMENT ONLY"/></View>}{createdNoc ? <Section title="Next steps" subtitle="Only the current server response is shown; payment, documents, and inspection are not started here."/> : <><Section title="Status journey" subtitle="This is a presentation-only fixture; no backend state is implied."/>{NOC_PRESENTATION_STATES.map((step,index)=><View key={step} style={s.timeline}><View style={[s.dot,index===0&&s.dotCurrent]}/><View style={s.timelineCopy}><Text style={s.cardTitle}>{step.replaceAll('_',' ')}</Text><Text style={s.small}>{index===0?'Local draft':'Example lifecycle state'}</Text></View></View>)}</>}<Pressable accessibilityRole="button" style={s.secondary} onPress={()=>navigate('documents')}><Text style={s.secondaryText}>Documents & upload state</Text></Pressable><Pressable accessibilityRole="button" style={s.secondary} onPress={()=>navigate('certificate')}><Text style={s.secondaryText}>Certificate & renewal details</Text></Pressable></ScrollView>;
  return <ScrollView contentContainerStyle={s.page}><Header title="Fire NOC services" subtitle="Create an application or continue a locally saved draft."/>{error ? <State message={error} tone="error"/> : null}<View style={s.hero}><Text style={s.heroEyebrow}>YOUR APPLICATIONS</Text><Text style={s.heroTitle}>Keep your service documents and progress together.</Text><Text style={s.heroCopy}>A local draft is stored on device. Submission and status require the configured FireSphere service.</Text></View><Primary title="Start a NOC draft" onPress={()=>navigate('form')}/><Section title="Saved draft" subtitle="Saved in encrypted on-device SQLite."/>{draft.title || draft.buildingName || draft.address || draft.documents.length ? <Pressable accessibilityRole="button" style={s.card} onPress={()=>navigate('form')}><Text style={s.cardTitle}>{draft.title || 'Untitled NOC draft'}</Text><Text style={s.small}>{draft.buildingName || 'Building name needed'} · {draft.address || 'Address needed'} · {draft.documents.length} document(s)</Text><Text style={s.action}>Continue draft ›</Text></Pressable> : <State message="No draft saved yet. Start a draft to explore the form and document flow."/>}<Section title="Sample tracking view" subtitle="Clearly marked static development fixture."/><Pressable accessibilityRole="button" style={s.card} onPress={()=>{setCreatedNoc(null);navigate('details')}}><Pill label="SAMPLE · NOT OFFICIAL"/><Text style={s.cardTitle}>Demo premises · Development record</Text><Text style={s.small}>Current presentation state: PAYMENT_PENDING</Text><Text style={s.action}>View sample status timeline ›</Text></Pressable></ScrollView>;
}

function Field(props: React.ComponentProps<typeof TextInput> & { label: string }) { const { label, style, ...input } = props; return <View style={s.field}><Text style={s.label}>{label}</Text><TextInput accessibilityLabel={label} style={[s.input,style]} {...input}/></View>; }
function Section({title,subtitle}:{title:string;subtitle:string}) { return <View style={s.section}><Text style={s.sectionTitle}>{title}</Text><Text style={s.small}>{subtitle}</Text></View>; }
function State({message,tone='neutral'}:{message:string;tone?:'neutral'|'error'}) { return <View accessibilityRole="alert" style={[s.state,tone==='error'&&s.stateError]}><Text style={tone==='error'?s.errorText:s.muted}>{message}</Text></View>; }
function Pill({label}:{label:string}) { return <View style={s.pill}><Text style={s.pillText}>{label}</Text></View>; }
function Primary({title,onPress,disabled=false}:{title:string;onPress:()=>void;disabled?:boolean}) { return <Pressable accessibilityRole="button" disabled={disabled} style={[s.primary,disabled&&s.disabled]} onPress={onPress}><Text style={s.primaryText}>{title}</Text></Pressable>; }

function restoreDraft(value: Record<string, unknown>): Draft {
  const previous = value.previousDraftValues && typeof value.previousDraftValues === 'object'
    ? value.previousDraftValues as Record<string, unknown>
    : Object.fromEntries(['applicant','buildingUse','floors'].filter((key)=>value[key] !== undefined).map((key)=>[key,value[key]]));
  return {
    title: typeof value.title === 'string' ? value.title : '',
    buildingName: typeof value.buildingName === 'string' ? value.buildingName : '',
    address: typeof value.address === 'string' ? value.address : '',
    description: typeof value.description === 'string' ? value.description : '',
    documents: Array.isArray(value.documents) ? value.documents as LocalDocument[] : [],
    ...(isNocApplication(value.createdApplication) ? { createdApplication: value.createdApplication } : {}),
    ...(Object.keys(previous).length ? { previousDraftValues: previous } : {}),
  };
}

function isNocApplication(value: unknown): value is NocApplication {
  if (!value || typeof value !== 'object') return false;
  const response = value as Partial<NocApplication>;
  return typeof response.id === 'string' && typeof response.title === 'string'
    && typeof response.buildingName === 'string' && typeof response.address === 'string'
    && typeof response.status === 'string' && typeof response.createdAt === 'string';
}

const s=StyleSheet.create({page:{padding:18,paddingBottom:44,gap:10},center:{minHeight:300,alignItems:'center',justifyContent:'center',gap:10},back:{minHeight:48,justifyContent:'center',alignSelf:'flex-start',paddingHorizontal:8},backText:{fontSize:14,color:'#0F1E36',fontWeight:'700'},eyebrow:{fontSize:10,letterSpacing:1.2,color:'#D32F2F',fontWeight:'800',marginTop:8},title:{fontSize:25,fontWeight:'800',color:'#0F1E36',marginTop:3},muted:{fontSize:13,color:'#64748B',lineHeight:19,marginTop:4},demo:{backgroundColor:'#FFF3E0',borderWidth:1,borderColor:'#FFE0B2',padding:12,borderRadius:9,marginVertical:9},demoText:{fontSize:11,color:'#9C3400',fontWeight:'700',lineHeight:16},hero:{backgroundColor:'#0F1E36',borderRadius:12,padding:18,marginTop:8},heroEyebrow:{color:'#e6a485',fontSize:10,fontWeight:'800',letterSpacing:1},heroTitle:{fontSize:20,color:'#fff',fontWeight:'800',marginTop:9,lineHeight:26},heroCopy:{fontSize:12,color:'#E2E8F0',marginTop:7,lineHeight:18},primary:{minHeight:50,backgroundColor:'#D32F2F',borderRadius:9,alignItems:'center',justifyContent:'center',paddingHorizontal:16,marginTop:7},primaryText:{color:'#fff',fontSize:14,fontWeight:'800'},secondary:{minHeight:48,borderWidth:1,borderColor:'#CBD5E1',borderRadius:8,justifyContent:'center',paddingHorizontal:13,backgroundColor:'#fff',marginTop:7},secondaryText:{fontSize:13,color:'#475569',fontWeight:'700'},disabled:{opacity:.55},field:{marginTop:9},label:{fontSize:13,color:'#334155',fontWeight:'700',marginBottom:6},input:{minHeight:48,borderWidth:1,borderColor:'#CBD5E1',borderRadius:8,paddingHorizontal:12,backgroundColor:'#fff',fontSize:16,color:'#0F1E36'},multiline:{minHeight:100,paddingTop:12,textAlignVertical:'top'},small:{fontSize:11,color:'#64748B',lineHeight:16,marginTop:5},section:{marginTop:15,marginBottom:1},sectionTitle:{fontSize:15,color:'#0F1E36',fontWeight:'800'},card:{backgroundColor:'#fff',borderWidth:1,borderColor:'#E2E8F0',borderRadius:10,padding:14,marginTop:5},cardTitle:{fontSize:13,color:'#0F1E36',fontWeight:'800',marginTop:4},action:{fontSize:12,color:'#D32F2F',fontWeight:'800',marginTop:8},actionTitle:{fontSize:14,color:'#0F1E36',fontWeight:'800',marginTop:10},danger:{fontSize:12,color:'#B71C1C',fontWeight:'800',marginTop:8},info:{backgroundColor:'#f5f8fa',borderColor:'#dce8ec',borderWidth:1,borderRadius:8,padding:13,marginVertical:9},infoTitle:{fontSize:12,color:'#354c54',fontWeight:'800',marginBottom:4},state:{backgroundColor:'#fff',borderWidth:1,borderColor:'#E2E8F0',borderRadius:9,padding:14,marginTop:8},stateError:{backgroundColor:'#fff0ed',borderColor:'#efc9c2'},errorText:{fontSize:12,color:'#B71C1C',lineHeight:18},row:{flexDirection:'row',justifyContent:'space-between',marginTop:10},pill:{alignSelf:'flex-start',backgroundColor:'#FFF3E0',paddingHorizontal:8,paddingVertical:5,borderRadius:5,marginTop:7},pillText:{fontSize:9,color:'#9C3400',fontWeight:'800',letterSpacing:.4},timeline:{flexDirection:'row',gap:10,minHeight:47},dot:{width:10,height:10,borderRadius:6,backgroundColor:'#E2E8F0',marginTop:5},dotCurrent:{backgroundColor:'#D32F2F'},timelineCopy:{flex:1,borderBottomWidth:1,borderBottomColor:'#E2E8F0',paddingBottom:8},certificate:{backgroundColor:'#fff',borderWidth:2,borderColor:'#d4b477',borderRadius:12,padding:19,alignItems:'center',gap:8,marginVertical:12},watermark:{color:'#b06e51',fontWeight:'900',letterSpacing:1,fontSize:12,transform:[{rotate:'-8deg'}],marginVertical:9},qr:{width:74,height:74,borderWidth:2,borderColor:'#59645e',alignItems:'center',justifyContent:'center',marginTop:9},qrText:{fontSize:20,color:'#39463f',fontWeight:'900'}});
