import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';
import type { DemoState, Match } from '../types';

export function WorkerDemoPage() {
  const [state, setState] = useState<DemoState>();
  const [matches, setMatches] = useState<Match[]>([]);
  const [busyMatch, setBusyMatch] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const current = await api.demoState();
      setState(current);
      setMatches(current.jobId ? await api.getMatches(current.jobId) : []);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '데모 정보를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function accept(matchId: string) {
    setBusyMatch(matchId);
    setError('');
    try {
      const updated = await api.workerAccept(matchId);
      setMatches((current) => current.map((match) => match.id === matchId ? updated : match));
      setNotice(updated.status === 'CONFIRMED' ? '양쪽 동의가 완료되어 매칭이 확정되었습니다.' : '수락을 기록했습니다.');
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '수락 처리에 실패했습니다.');
    } finally {
      setBusyMatch(undefined);
    }
  }

  const approvedMatches = matches.filter((match) => match.farmerApproved);
  const job = state?.job;

  return (
    <main className="container">
      <div className="page-heading">
        <Link to="/">← 처음으로</Link>
        <span className="badge">로그인 없는 데모</span>
      </div>
      <section className="card">
        <h1>구직자 데모</h1>
        <p>농가가 승인한 작업을 확인하고, 참여 여부를 직접 결정합니다. 데모 수락은 실제 고용 계약을 만들지 않습니다.</p>
        {error && <p className="alert error" role="alert">{error}</p>}
        {notice && <p className="alert success" role="status">{notice}</p>}

        {loading && !state ? (
          <div className="alert" role="status">저장된 작업 정보를 불러오는 중입니다.</div>
        ) : !state && error ? (
          <button type="button" className="secondary" onClick={() => void refresh()}>다시 시도</button>
        ) : !state?.jobId ? (
          <div className="alert warning" role="status">
            아직 등록된 작업이 없습니다. <Link to="/farmer-demo">농가 데모에서 작업을 등록해 주세요.</Link>
          </div>
        ) : matches.length === 0 ? (
          <div className="alert warning" role="status">
            아직 추천된 후보가 없습니다. <Link to="/farmer-demo">농가 데모에서 요청 조건을 확인해 주세요.</Link>
          </div>
        ) : approvedMatches.length === 0 ? (
          <div className="alert warning" role="status">
            농가 승인 대기 중입니다. <Link to="/farmer-demo">농가 데모에서 후보를 승인해 주세요.</Link>
          </div>
        ) : (
          <>
            <h2>농가 승인 작업</h2>
            <dl className="details-grid">
              <dt>작업</dt><dd>{job?.workType ?? '-'}</dd>
              <dt>위치</dt><dd>{job?.location ?? '-'}</dd>
              <dt>기간</dt><dd>{job?.startDate ?? '-'} ~ {job?.endDate ?? '-'}</dd>
              <dt>일급</dt><dd>{job?.dailyPay != null ? `${Number(job.dailyPay).toLocaleString('ko-KR')}원` : '-'}</dd>
            </dl>
            <div className="grid section-gap">
              {approvedMatches.map((match) => (
                <article className="candidate" key={match.id}>
                  <div className="candidate-heading">
                    <h3>{match.nickname}</h3>
                    <span className="badge">추천 점수 {match.totalScore}</span>
                  </div>
                  <ul>{match.scoreReasons.map((reason, index) => <li key={`${index}-${reason}`}>{reason}</li>)}</ul>
                  {match.status === 'CONFIRMED' ? (
                    <p className="status-confirmed">농가와 구직자 양쪽이 동의했습니다. 매칭이 확정되었습니다.</p>
                  ) : match.workerAccepted ? (
                    <p>수락 완료 · 상태를 새로고침해 확인할 수 있습니다.</p>
                  ) : (
                    <button type="button" onClick={() => accept(match.id)} disabled={busyMatch === match.id}>
                      {busyMatch === match.id ? '처리 중…' : '이 작업 수락하기'}
                    </button>
                  )}
                  {match.status === 'CONFIRMED' && <Link to={`/matching/${encodeURIComponent(match.id)}`}>진행 상태 보기 →</Link>}
                </article>
              ))}
            </div>
          </>
        )}
        <button type="button" className="secondary section-gap" onClick={() => void refresh()} disabled={loading}>
          {loading ? '새로고침 중…' : '상태 새로고침'}
        </button>
      </section>
    </main>
  );
}
