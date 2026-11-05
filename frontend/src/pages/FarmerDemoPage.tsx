import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';
import type { DemoState, JobAnalysisResponse, Match } from '../types';

const exampleRequest = '청도군 매전면에서 복숭아 적과 인력을 3명 구합니다. 2026년 11월 9일부터 11일까지 하루 12만 원이고 식사를 제공합니다. 작업 전에 농기계 안전 점검이 필요합니다.';

export function FarmerDemoPage() {
  const [rawRequest, setRawRequest] = useState(exampleRequest);
  const [result, setResult] = useState<JobAnalysisResponse>();
  const [state, setState] = useState<DemoState>();
  const [matches, setMatches] = useState<Match[]>([]);
  const [jobId, setJobId] = useState<string>();
  const [safetyAcknowledged, setSafetyAcknowledged] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const current = await api.demoState();
      const currentMatches = current.jobId ? await api.getMatches(current.jobId) : [];
      setState({ ...current, matches: currentMatches });
      setJobId(current.jobId ?? undefined);
      setMatches(currentMatches);
      setSafetyAcknowledged(current.safetyConfirmed);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '저장된 작업 정보를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function analyze() {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      setResult(await api.analyzeJob({ rawRequest }));
      setState(undefined);
      setMatches([]);
      setJobId(undefined);
      setSafetyAcknowledged(false);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '요청 분석에 실패했습니다.');
    } finally {
      setBusy(false);
    }
  }

  async function saveJob() {
    if (!result) return;
    setBusy(true);
    setError('');
    try {
      const created = await api.createJob(result.analysisId);
      setJobId(created.id);
      setMatches(created.matches);
      setState({
        jobId: created.id,
        job: created.job,
        safetyConfirmed: created.safetyConfirmed,
        matches: created.matches,
      });
      setNotice(created.matches.length ? '조건에 맞는 후보를 찾았습니다. 검토 후 승인해 주세요.' : '조건에 맞는 후보가 없습니다. 요청 내용을 확인해 주세요.');
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '작업 저장에 실패했습니다.');
    } finally {
      setBusy(false);
    }
  }

  async function confirmSafety() {
    if (!jobId) return;
    setBusy(true);
    setError('');
    try {
      await api.confirmSafety(jobId, true);
      setSafetyAcknowledged(true);
      setState((current) => current ? { ...current, safetyConfirmed: true } : current);
      setNotice('안전 확인을 기록했습니다. 이제 후보를 승인할 수 있습니다.');
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '안전 확인을 저장하지 못했습니다.');
    } finally {
      setBusy(false);
    }
  }

  async function approve(matchId: string) {
    setBusy(true);
    setError('');
    try {
      const updated = await api.farmerApprove(matchId);
      setMatches((current) => current.map((match) => match.id === matchId ? updated : match));
      setState((current) => current
        ? { ...current, matches: current.matches.map((match) => match.id === matchId ? updated : match) }
        : current);
      setNotice('농가 승인을 완료했습니다. 구직자 데모에서 수락하면 매칭이 확정됩니다.');
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '후보 승인에 실패했습니다.');
    } finally {
      setBusy(false);
    }
  }

  const analysis = result?.analysis;
  const job = analysis?.job ?? state?.job;
  const hasMissing = Boolean(job?.missingFields?.length);
  const safetyRequired = Boolean(job?.safetyReview?.confirmationRequired || job?.hazardFlags?.length);

  return (
    <main className="container">
      <div className="page-heading">
        <Link to="/">← 처음으로</Link>
        <span className="badge">로그인 없는 데모</span>
      </div>
      <section className="card">
        <h1>농가 요청 입력</h1>
        <p>작업 조건을 자연어로 적어 주세요. 데모 승인과 수락은 실제 고용 계약이나 연락을 만들지 않습니다.</p>
        <label htmlFor="job-request">작업 요청</label>
        <textarea id="job-request" rows={5} value={rawRequest} onChange={(event) => setRawRequest(event.target.value)} />
        <div className="row" style={{ marginTop: 16 }}>
          <button type="button" onClick={analyze} disabled={busy || !rawRequest.trim()}>
            {busy ? '분석 중…' : 'AI 조건 분석'}
          </button>
        </div>
      </section>

      {error && <p className="alert error" role="alert">{error}</p>}
      {notice && <p className="alert success" role="status">{notice}</p>}
      {loading && !state && <p className="alert" role="status">저장된 작업 상태를 불러오는 중입니다.</p>}
      {!loading && error && !state && (
        <button type="button" className="secondary" onClick={() => void refresh()}>다시 시도</button>
      )}

      {job && (
        <section className="card section-gap">
          <h2>AI 분석 결과</h2>
          <p>추출한 조건을 확인해 주세요. 알 수 없는 정보는 추측하지 않습니다.</p>
          {hasMissing ? (
            <div className="alert warning" role="status">
              다음 정보가 필요합니다: {job.missingFields?.join(', ')}. 작업 요청에 내용을 추가한 뒤 다시 분석해 주세요.
            </div>
          ) : (
            <>
              <dl className="details-grid">
                <dt>작업</dt><dd>{job.workType ?? '-'}</dd>
                <dt>위치</dt><dd>{job.location ?? '-'}</dd>
                <dt>기간</dt><dd>{job.startDate ?? '-'} ~ {job.endDate ?? '-'}</dd>
                <dt>인원</dt><dd>{job.workersNeeded != null ? `${job.workersNeeded}명` : '-'}</dd>
                <dt>일급</dt><dd>{job.dailyPay != null ? `${job.dailyPay.toLocaleString('ko-KR')}원` : '-'}</dd>
                <dt>식사</dt><dd>{job.mealProvided == null ? '확인되지 않음' : job.mealProvided ? '제공' : '미제공'}</dd>
              </dl>
              {safetyRequired && (
                <div className="alert warning">
                  <strong>작업 안전 확인이 필요합니다.</strong>
                  {job.safetyReview?.requiredChecks.length ? (
                    <ul>{job.safetyReview.requiredChecks.map((check, index) => <li key={`${index}-${check}`}>{check}</li>)}</ul>
                  ) : null}
                  {job.hazardFlags?.map((flag, index) => <p key={`${index}-${flag}`}>확인 항목: {flag}</p>)}
                </div>
              )}
              {!jobId && !loading && (
                <button type="button" onClick={saveJob} disabled={busy}>
                  {busy ? '후보 확인 중…' : '조건을 확인하고 후보 찾기'}
                </button>
              )}
            </>
          )}
        </section>
      )}

      {jobId && safetyRequired && !safetyAcknowledged && (
        <section className="card section-gap">
          <h2>안전 확인</h2>
          <p>표시된 안전 항목을 확인한 뒤 후보 승인 단계로 진행할 수 있습니다.</p>
          <button type="button" onClick={confirmSafety} disabled={busy}>안전 항목을 확인했습니다</button>
        </section>
      )}

      {matches.length > 0 && (
        <section className="card section-gap">
          <h2>추천 후보</h2>
          <p>점수는 날짜 30점, 작업 25점, 이동 20점, 경험 15점, 임금 10점 기준입니다.</p>
          <div className="grid">
            {matches.map((match) => (
              <article className="candidate" key={match.id}>
                <div className="candidate-heading">
                  <h3>{match.nickname}</h3>
                  <span className="badge">점수 {match.totalScore}</span>
                </div>
                <ul>{match.scoreReasons.map((reason, index) => <li key={`${index}-${reason}`}>{reason}</li>)}</ul>
                {match.status === 'CONFIRMED' ? (
                  <p className="status-confirmed">양쪽 동의로 매칭이 확정되었습니다.</p>
                ) : match.farmerApproved ? (
                  <p>농가 승인 완료 · 구직자 수락 대기</p>
                ) : (
                  <button type="button" onClick={() => approve(match.id)} disabled={busy || (safetyRequired && !safetyAcknowledged)}>
                    후보 승인
                  </button>
                )}
              </article>
            ))}
          </div>
          {matches.some((match) => match.farmerApproved) && <Link to="/worker-demo">구직자 데모로 이동해 수락하기 →</Link>}
        </section>
      )}
      {jobId && matches.length === 0 && (
        <div className="alert warning section-gap" role="status">
          추천 후보가 없습니다. 요청 조건을 조정한 뒤 위에서 다시 분석해 주세요.
        </div>
      )}
      {!loading && state?.jobId && (
        <button type="button" className="secondary section-gap" onClick={() => void refresh()} disabled={busy || loading}>
          상태 새로고침
        </button>
      )}
    </main>
  );
}
