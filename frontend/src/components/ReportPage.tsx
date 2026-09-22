import React, { useState } from 'react';
import { useKeycloak } from '@react-keycloak/web';

interface ReportRow {
  reportDate: string;
  prosthesisId: string;
  totalActivations: number;
  avgResponseTimeMs: number;
  maxResponseTimeMs: number;
  minResponseTimeMs: number;
  avgBatteryLevel: number;
  totalUsageMinutes: number;
}

interface Report {
  userId: string;
  from: string;
  to: string;
  generatedAt: string;
  rows: ReportRow[];
}

interface ApiError {
  error?: string;
  message?: string;
}

const formatDate = (date: Date): string => date.toISOString().slice(0, 10);

const defaultFrom = (): string => {
  const date = new Date();
  date.setDate(date.getDate() - 6);
  return formatDate(date);
};

const defaultTo = (): string => formatDate(new Date());

const ReportPage: React.FC = () => {
  const { keycloak, initialized } = useKeycloak();
  const [from, setFrom] = useState<string>(defaultFrom);
  const [to, setTo] = useState<string>(defaultTo);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [report, setReport] = useState<Report | null>(null);

  const apiUrl = process.env.REACT_APP_API_URL || 'http://localhost:8000';

  const getReport = async (): Promise<Report> => {
    if (!keycloak?.token) {
      throw new Error('Not authenticated');
    }
    const response = await fetch(
      `${apiUrl}/reports?from=${from}&to=${to}`,
      {
        headers: {
          'Authorization': `Bearer ${keycloak.token}`
        }
      }
    );

    if (!response.ok) {
      const body: ApiError = await response.json().catch(() => ({}));
      throw new Error(body.message || `Request failed with status ${response.status}`);
    }
    return response.json();
  };

  const handleGetReport = async () => {
    if (!from || !to) {
      setError('Please select the report period');
      return;
    }
    try {
      setLoading(true);
      setError(null);
      const data = await getReport();
      setReport(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setLoading(false);
    }
  };

  const handleDownloadCsv = async () => {
    if (!keycloak?.token) {
      setError('Not authenticated');
      return;
    }
    try {
      setLoading(true);
      setError(null);
      const response = await fetch(
        `${apiUrl}/reports?from=${from}&to=${to}&format=csv`,
        {
          headers: {
            'Authorization': `Bearer ${keycloak.token}`
          }
        }
      );

      if (!response.ok) {
        const body: ApiError = await response.json().catch(() => ({}));
        throw new Error(body.message || `Request failed with status ${response.status}`);
      }

      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `report_${from}_${to}.csv`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setLoading(false);
    }
  };

  if (!initialized) {
    return <div className="flex items-center justify-center min-h-screen bg-gray-100">Loading...</div>;
  }

  if (!keycloak.authenticated) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
        <button
          onClick={() => keycloak.login()}
          className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
        >
          Login
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
      <div className="p-8 bg-white rounded-lg shadow-md w-full max-w-3xl">
        <h1 className="text-2xl font-bold mb-6">Usage Reports</h1>

        <div className="flex items-end gap-4 mb-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="from">
              From
            </label>
            <input
              id="from"
              type="date"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              className="px-3 py-2 border border-gray-300 rounded"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="to">
              To
            </label>
            <input
              id="to"
              type="date"
              value={to}
              onChange={(e) => setTo(e.target.value)}
              className="px-3 py-2 border border-gray-300 rounded"
            />
          </div>
          <button
            onClick={handleGetReport}
            disabled={loading}
            className={`px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 ${
              loading ? 'opacity-50 cursor-not-allowed' : ''
            }`}
          >
            {loading ? 'Generating Report...' : 'Get Report'}
          </button>
          <button
            onClick={handleDownloadCsv}
            disabled={loading}
            className={`px-4 py-2 bg-green-500 text-white rounded hover:bg-green-600 ${
              loading ? 'opacity-50 cursor-not-allowed' : ''
            }`}
          >
            Download CSV
          </button>
        </div>

        {error && (
          <div className="mt-4 p-4 bg-red-100 text-red-700 rounded">
            {error}
          </div>
        )}

        {report && (
          <div className="mt-6">
            <h2 className="text-lg font-semibold mb-2">
              Report for {report.userId} ({report.from} — {report.to})
            </h2>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200 text-sm">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-3 py-2 text-left">Date</th>
                    <th className="px-3 py-2 text-left">Prosthesis</th>
                    <th className="px-3 py-2 text-right">Activations</th>
                    <th className="px-3 py-2 text-right">Avg resp. (ms)</th>
                    <th className="px-3 py-2 text-right">Max resp. (ms)</th>
                    <th className="px-3 py-2 text-right">Min resp. (ms)</th>
                    <th className="px-3 py-2 text-right">Battery (%)</th>
                    <th className="px-3 py-2 text-right">Usage (min)</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {report.rows.map((row) => (
                    <tr key={`${row.reportDate}-${row.prosthesisId}`}>
                      <td className="px-3 py-2">{row.reportDate}</td>
                      <td className="px-3 py-2">{row.prosthesisId}</td>
                      <td className="px-3 py-2 text-right">{row.totalActivations}</td>
                      <td className="px-3 py-2 text-right">{row.avgResponseTimeMs.toFixed(1)}</td>
                      <td className="px-3 py-2 text-right">{row.maxResponseTimeMs}</td>
                      <td className="px-3 py-2 text-right">{row.minResponseTimeMs}</td>
                      <td className="px-3 py-2 text-right">{row.avgBatteryLevel.toFixed(1)}</td>
                      <td className="px-3 py-2 text-right">{row.totalUsageMinutes}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ReportPage;